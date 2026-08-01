package com.novelchat.domain.engine

import com.novelchat.data.model.CharacterFieldDefinition
import com.novelchat.data.model.CharacterFieldPolicy
import com.novelchat.data.model.PlayerChanges
import com.novelchat.data.model.StoryStateUpdate
import com.novelchat.data.model.TaskAssignment
import com.novelchat.data.model.WorldSetting

internal class EmptyTaskAssignmentException(message: String) : IllegalArgumentException(message)
internal class IncompleteStateSyncException(
    val missingFields: List<String>
) : IllegalArgumentException("状态同步遗漏：${missingFields.joinToString("、")}")

internal object TaskAssignmentQualityPolicy {

    fun requireMeaningful(assignment: TaskAssignment): TaskAssignment {
        if (!isMeaningful(assignment)) {
            throw EmptyTaskAssignmentException("任务分配没有任何可执行的状态变化")
        }
        return assignment
    }

    fun requireNarrativeConsistency(
        worldSetting: WorldSetting,
        narrative: String,
        assignment: TaskAssignment
    ): TaskAssignment {
        val missing = buildList {
            addAll(missingDynamicPlayerUpdates(worldSetting, narrative, assignment))
            if (requiresTimeUpdate(narrative) && assignment.storyUpdate?.timeOfDay.isNullOrBlank()) {
                add("场景时间(time_of_day)")
            }
        }
        if (missing.isNotEmpty()) throw IncompleteStateSyncException(missing)
        return assignment
    }

    fun missingDynamicPlayerUpdates(
        worldSetting: WorldSetting,
        narrative: String,
        assignment: TaskAssignment
    ): List<String> {
        if (narrative.isBlank()) return emptyList()
        val changes = assignment.playerUpdate?.changes
        return CharacterFieldPolicy.effectiveDefinitions(worldSetting.characterFields)
            .asSequence()
            .filter { field ->
                field.updateMode == CharacterFieldDefinition.UPDATE_DYNAMIC &&
                    CharacterFieldPolicy.appliesTo(field, CharacterFieldDefinition.TARGET_PLAYER) &&
                    isClearlyTriggered(field, narrative) &&
                    !hasExplicitPlayerValue(field.key, changes)
            }
            .map { field -> "${field.label}(${field.key})" }
            .toList()
    }

    fun isMeaningful(assignment: TaskAssignment): Boolean {
        return hasCertifiedNoChange(assignment) ||
            assignment.storyUpdate?.let(::hasStoryChanges) == true ||
            assignment.playerUpdate?.let { update ->
                hasPlayerChanges(update.changes)
            } == true ||
            assignment.newCharacters.any { it.name.isNotBlank() } ||
            assignment.retiredCharacters.any { it.name.isNotBlank() } ||
            assignment.characterUpdates.any { it.name.isNotBlank() } ||
            assignment.worldbookUpdates.any(::hasWorldbookChange) ||
            assignment.itemTransfers.any {
                it.item.isNotBlank() && it.quantity > 0 &&
                    it.from.isNotBlank() && it.to.isNotBlank() && it.from.trim() != it.to.trim()
            } ||
            assignment.events.any { event ->
                event.type.isNotBlank() &&
                    event.target.isNotBlank() &&
                    event.description.isNotBlank()
            }
    }

    private fun hasCertifiedNoChange(assignment: TaskAssignment): Boolean {
        val reason = assignment.noChangeReason.trim()
        return assignment.syncStatus.trim().lowercase() in setOf("no_change", "unchanged") &&
            reason.length >= 8 &&
            reason !in genericNoChangeReasons
    }

    private val genericNoChangeReasons = setOf(
        "没有变化", "无状态变化", "无需更新", "没有需要更新", "no change", "unchanged"
    )

    private fun hasStoryChanges(update: StoryStateUpdate): Boolean {
        return !update.currentLocation.isNullOrBlank() ||
            !update.timeOfDay.isNullOrBlank() ||
            !update.weather.isNullOrBlank() ||
            !update.mood.isNullOrBlank() ||
            !update.sceneDescription.isNullOrBlank() ||
            update.activeCharacters != null ||
            update.pendingEvents != null
    }

    private fun hasPlayerChanges(changes: PlayerChanges): Boolean {
        return changes.inventory.any {
            it.action.trim().lowercase() in setOf("add", "remove") &&
                it.item.isNotBlank() && it.quantity > 0
        } ||
            changes.relationships.any { (name, patch) ->
                name.isNotBlank() && PlayerStatePatchPolicy.hasRelationshipChange(patch)
            } ||
            changes.questLog != null ||
            changes.storyProgress?.let(PlayerStatePatchPolicy::hasStoryProgressChange) == true ||
            changes.physicalState?.let { patch ->
                patch.stamina != null ||
                    !patch.health.isNullOrBlank() ||
                    patch.injuries != null ||
                    !patch.fatigue.isNullOrBlank() ||
                    !patch.hunger.isNullOrBlank() ||
                    !patch.pain.isNullOrBlank()
            } == true ||
            changes.mentalState?.let { patch ->
                !patch.mood.isNullOrBlank() ||
                    !patch.stress.isNullOrBlank() ||
                    !patch.focus.isNullOrBlank() ||
                    !patch.currentThoughts.isNullOrBlank()
            } == true ||
            changes.attributes.isNotEmpty() ||
            changes.risk != null
    }

    private fun hasWorldbookChange(update: com.novelchat.data.model.WorldbookUpdate): Boolean {
        return when (update.action.trim().lowercase()) {
            "create" -> !update.name.isNullOrBlank() && !update.description.isNullOrBlank()
            "update", "delete" -> !update.id.isNullOrBlank() || !update.name.isNullOrBlank()
            else -> false
        }
    }

    private fun isClearlyTriggered(field: CharacterFieldDefinition, narrative: String): Boolean {
        val contract = "${field.key} ${field.label} ${field.description}".lowercase()
        val text = narrative.lowercase()
        return triggerProfiles.any { profile ->
            contract.containsAny(profile.fieldTerms) && text.containsAffirmed(profile.eventTerms)
        }
    }

    private fun hasExplicitPlayerValue(key: String, changes: PlayerChanges?): Boolean {
        if (changes == null) return false
        return when (key) {
            "stamina" -> changes.physicalState?.stamina != null
            "health" -> changes.physicalState?.health != null
            "fatigue" -> changes.physicalState?.fatigue != null
            "hunger" -> changes.physicalState?.hunger != null
            "pain" -> changes.physicalState?.pain != null
            "mood" -> changes.mentalState?.mood != null
            "stress" -> changes.mentalState?.stress != null
            "focus" -> changes.mentalState?.focus != null
            "current_thoughts" -> changes.mentalState?.currentThoughts != null
            "risk" -> changes.risk != null
            else -> changes.attributes.containsKey(key)
        }
    }

    private fun requiresTimeUpdate(narrative: String): Boolean {
        return narrative.lowercase().containsAffirmed(
            setOf("天亮", "晨光", "清晨", "黎明", "翌日", "次日早", "until dawn", "next morning")
        )
    }

    private fun String.containsAny(terms: Set<String>): Boolean = terms.any(::contains)

    private fun String.containsAffirmed(terms: Set<String>): Boolean {
        return terms.any { term ->
            var index = indexOf(term)
            while (index >= 0) {
                val contextStart = (index - 6).coerceAtLeast(0)
                val contextEnd = (index + term.length + 8).coerceAtMost(length)
                val context = substring(contextStart, contextEnd)
                if (negativeTriggerTerms.none(context::contains)) return@any true
                index = indexOf(term, index + term.length)
            }
            false
        }
    }

    private data class TriggerProfile(
        val fieldTerms: Set<String>,
        val eventTerms: Set<String>
    )

    private val triggerProfiles = listOf(
        TriggerProfile(
            fieldTerms = setOf("risk", "风险", "暴露", "警戒", "通缉", "发现概率"),
            eventTerms = setOf(
                "警报", "警铃", "搜查", "巡逻", "追捕", "通缉", "被发现", "暴露",
                "监控记录", "镜录仪", "alarm", "patrol", "manhunt", "caught", "exposed"
            )
        ),
        TriggerProfile(
            fieldTerms = setOf("stamina", "体力", "精力", "魔力", "法力", "能量储备", "mana", "energy"),
            eventTerms = setOf(
                "施法", "施放", "施展", "催动", "念出咒", "奔跑", "冲刺", "攀爬", "搏斗",
                "休息", "睡眠", "入睡", "睡去", "醒来", "恢复体力", "恢复魔力",
                "cast a spell", "casts a spell", "rested", "sleep", "fell asleep", "woke up"
            )
        ),
        TriggerProfile(
            fieldTerms = setOf("fatigue", "疲劳", "困倦", "倦意"),
            eventTerms = setOf(
                "奔跑", "冲刺", "攀爬", "搏斗", "熬夜", "休息", "睡眠", "入睡", "睡去", "醒来",
                "exhausted", "rested", "sleep", "fell asleep", "woke up"
            )
        ),
        TriggerProfile(
            fieldTerms = setOf("稳定", "控制", "精准", "专注度", "spell_control"),
            eventTerms = setOf("施法", "施放", "施展", "催动", "念出咒", "cast a spell", "casts a spell")
        ),
        TriggerProfile(
            fieldTerms = setOf("侵蚀", "污染", "腐化", "残留", "taint", "corruption"),
            eventTerms = setOf("侵蚀", "污染", "腐化", "回声层", "禁术", "异常力量", "taint", "corruption")
        ),
        TriggerProfile(
            fieldTerms = setOf("誓约", "契约冲突", "违约", "oath_strain"),
            eventTerms = setOf("誓约", "契约", "违背", "违规", "禁令", "封锁", "oath")
        ),
        TriggerProfile(
            fieldTerms = setOf("压力", "负荷", "紧张", "stress"),
            eventTerms = setOf("警报", "追捕", "审讯", "威胁", "恐惧", "惊慌", "alarm", "chased")
        ),
        TriggerProfile(
            fieldTerms = setOf("健康", "伤势", "疼痛", "health", "pain"),
            eventTerms = setOf("受伤", "流血", "骨折", "灼伤", "击中", "刺伤", "injured", "wounded")
        )
    )

    private val negativeTriggerTerms = setOf(
        "没有", "并未", "未曾", "尚未", "不再", "再无", "解除", "平息", "结束", "消失", "远去",
        "避免", "躲过", "没有被", "did not", "didn't", "was not", "without", "avoided", "ended"
    )
}
