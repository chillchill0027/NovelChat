package com.novelchat.domain.v2.state

import com.google.gson.Gson
import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.CharacterFieldDefinition
import com.novelchat.data.model.CharacterFieldPolicy
import com.novelchat.data.model.CharacterLore
import com.novelchat.data.model.CharacterNamingPolicy
import com.novelchat.data.model.CharacterReferencePolicy
import com.novelchat.data.model.MemoryNote
import com.novelchat.data.model.Relationship
import com.novelchat.data.model.RelationshipStatePatch
import com.novelchat.data.model.StatusHistory
import com.novelchat.data.model.SummaryEntry
import com.novelchat.data.model.WorldbookContentPolicy
import com.novelchat.data.model.WorldbookEntry
import com.novelchat.data.repo.EntityWrite
import com.novelchat.data.repo.StorySnapshot
import com.novelchat.domain.engine.CharacterAttributeMapper
import com.novelchat.domain.engine.CharacterProfilePolicy
import com.novelchat.domain.engine.InventoryTransferPolicy
import com.novelchat.domain.engine.PlayerGoalPolicy
import com.novelchat.domain.engine.PlayerStatePatchPolicy
import com.novelchat.domain.engine.StateSyncPolicy
import com.novelchat.domain.engine.TaskAssignmentRosterPolicy
import java.nio.charset.StandardCharsets
import java.util.UUID

object StateDeltaApplier {

    private val gson = Gson()
    private val goalNoise = Regex("""[\s，,。\.！!？\?；;：:“”"'（）()\[\]【】—\-…]""")
    private val importantMemoryKeywords = Regex(
        """承诺|约定|答应|秘密|偷|背叛|冲突|吵|交易|借|欠|钥匙|合同|誓约|警告|威胁|交付|面试"""
    )

    private const val MAX_MEMORIES_PER_NPC = 30
    private const val MAX_PENDING_EVENTS = 8
    private const val MAX_DELTA_PER_ROUND = 15

    fun apply(
        base: StorySnapshot,
        delta: StateDelta,
        rounds: List<Int>,
        now: String,
        clearLegacyPendingFlag: Boolean = false
    ): ApplyResult {
        val issues = mutableListOf<ApplyIssue>()
        val targetRound = rounds.maxOrNull() ?: base.story.currentRound.coerceAtLeast(1)
        val targetNarrativeText = base.narratives
            .filter { it.turn in rounds }
            .joinToString("\n") { it.narrative }
        val playerName = base.player.name.ifBlank { CharacterNamingPolicy.DEFAULT_PLAYER_NAME }
        val fieldsContract = base.world.characterFields

        var player = base.player
        var story = base.story
        val cardList = base.characters.toMutableList()
        val worldbookList = base.worldbook.toMutableList()

        fun cardKey(card: CharacterCard): String = card.id.ifBlank { card.name }

        fun findCardIndex(rawName: String): Int? {
            val trimmed = rawName.trim()
            if (trimmed.isBlank()) return null
            val exact = cardList.indexOfFirst { it.name == trimmed || it.id == trimmed }
            if (exact >= 0) return exact
            val resolved = CharacterReferencePolicy.resolve(cardList, trimmed) ?: return null
            val idx = cardList.indexOfFirst { cardKey(it) == cardKey(resolved) }
            return idx.takeIf { it >= 0 }
        }

        fun isPlayerAlias(rawName: String): Boolean {
            val trimmed = rawName.trim()
            return trimmed == playerName || trimmed in setOf("主角", "我", "玩家")
        }

        // 1. 新建 NPC（若已存在则转入更新）
        val mergedNpcsFromNew = mutableListOf<NpcDelta>()
        val createdNames = mutableListOf<String>()
        delta.newNpcs.orEmpty().forEach { newNpc ->
            val rawName = newNpc.name.trim()
            if (rawName.isBlank() || CharacterNamingPolicy.isGenericNpcName(rawName)) {
                issues += ApplyIssue(
                    ApplyIssueLevel.WARN,
                    "new_npcs:${rawName.ifBlank { "blank" }}",
                    "跳过空名或占位称呼的新 NPC"
                )
                return@forEach
            }
            val existingIdx = findCardIndex(rawName)
            if (existingIdx != null) {
                val existing = cardList[existingIdx]
                mergedNpcsFromNew += NpcDelta(
                    name = existing.name,
                    relationshipStage = newNpc.relationshipToPlayer.takeIf { it.isNotBlank() },
                    fields = newNpc.fields
                )
                return@forEach
            }
            val relStage = newNpc.relationshipToPlayer.trim().ifBlank { "初识" }
            val rawCard = CharacterCard(
                id = rawName,
                name = rawName,
                status = "active",
                age = newNpc.age ?: 0,
                gender = newNpc.gender.trim(),
                occupation = newNpc.identity.trim(),
                personality = newNpc.personality.trim(),
                appearance = newNpc.appearance.trim(),
                lore = CharacterLore(speechStyle = newNpc.speechStyle.trim()),
                relationshipStatus = TaskAssignmentRosterPolicy.relationshipStatusFor(relStage),
                relationships = mapOf(playerName to Relationship(stage = relStage)),
                customAttributes = CharacterFieldPolicy.normalizeImportedAttributes(
                    emptyMap(),
                    fieldsContract,
                    CharacterFieldDefinition.TARGET_NPC
                ),
                createdBy = "ai",
                createdAt = now,
                lastModified = now
            )
            val normalized = CharacterProfilePolicy.reconcile(
                CharacterAttributeMapper.validateCharacterCard(
                    CharacterAttributeMapper.applyNpcAttributes(
                        card = rawCard,
                        attributes = newNpc.fields.orEmpty(),
                        characterFields = fieldsContract,
                        playerName = playerName,
                        includeManual = true
                    ),
                    fieldsContract
                ),
                playerName
            )
            cardList += normalized
            createdNames += normalized.name
        }

        // 2. 更新 NPC（含情绪、好感/信任增量、自定义字段、记忆、物品）
        (delta.npcs.orEmpty() + mergedNpcsFromNew).forEach { npcDelta ->
            val rawName = npcDelta.name.trim()
            if (rawName.isBlank() || isPlayerAlias(rawName)) return@forEach

            var idx = findCardIndex(rawName)
            if (idx == null) {
                val appearsInNarrative = targetNarrativeText.contains(rawName)
                if (!CharacterNamingPolicy.isGenericNpcName(rawName) && appearsInNarrative) {
                    val minimal = CharacterProfilePolicy.reconcile(
                        CharacterAttributeMapper.validateCharacterCard(
                            CharacterCard(
                                id = rawName,
                                name = rawName,
                                status = "active",
                                createdBy = "ai",
                                createdAt = now,
                                lastModified = now
                            ),
                            fieldsContract
                        ),
                        playerName
                    )
                    cardList += minimal
                    idx = cardList.lastIndex
                    createdNames += minimal.name
                    issues += ApplyIssue(
                        ApplyIssueLevel.INFO,
                        "npcs:$rawName",
                        "正文中出现未建档实名人物，已自动创建基础角色卡"
                    )
                } else {
                    issues += ApplyIssue(
                        ApplyIssueLevel.WARN,
                        "npcs:$rawName",
                        "未能定位角色且未在正文中确认为有效实名，已跳过"
                    )
                    return@forEach
                }
            }

            var card = cardList[idx]
            val currentRel = card.relationships[playerName]
                ?: Relationship(favor = card.favor, stage = card.relationshipStatus.ifBlank { "陌生人" })
            val boundedFavorDelta = (npcDelta.favorDelta ?: 0).coerceIn(-MAX_DELTA_PER_ROUND, MAX_DELTA_PER_ROUND)
            val boundedTrustDelta = (npcDelta.trustDelta ?: 0).coerceIn(-MAX_DELTA_PER_ROUND, MAX_DELTA_PER_ROUND)
            val nextStage = npcDelta.relationshipStage?.trim()?.takeIf { it.isNotBlank() } ?: currentRel.stage
            val updatedRel = currentRel.copy(
                favor = (currentRel.favor + boundedFavorDelta).coerceIn(-100, 100),
                trust = (currentRel.trust + boundedTrustDelta).coerceIn(0, 100),
                stage = nextStage
            )

            var inv = card.inventory
            npcDelta.inventory.orEmpty().forEach { op ->
                val itemName = op.item.trim()
                if (itemName.isBlank()) return@forEach
                when (op.op.trim().lowercase()) {
                    "add" -> inv = CharacterAttributeMapper.addItems(inv, itemName, op.qty.coerceAtLeast(1))
                    "remove" -> {
                        val hasItem = inv.any { it.name == itemName }
                        if (!hasItem) {
                            issues += ApplyIssue(
                                ApplyIssueLevel.INFO,
                                "npc_inventory:${card.name}:$itemName",
                                "NPC 不持有该物品，忽略移除"
                            )
                        } else {
                            inv = CharacterAttributeMapper.removeItems(inv, itemName, op.qty.coerceAtLeast(1))
                        }
                    }
                    else -> issues += ApplyIssue(ApplyIssueLevel.WARN, "npc_inventory:${card.name}", "未知库存操作：${op.op}")
                }
            }

            val nextMood = npcDelta.emotion?.trim()?.takeIf { it.isNotBlank() }
            val updatedMemories = appendNpcMemory(
                current = card.memories,
                rawMemory = npcDelta.memory,
                turn = targetRound,
                favorDelta = boundedFavorDelta
            )

            card = card.copy(
                emotion = nextMood ?: card.emotion,
                favor = updatedRel.favor,
                relationshipStatus = if (npcDelta.relationshipStage != null) {
                    TaskAssignmentRosterPolicy.relationshipStatusFor(nextStage).takeIf { it != "未知" }
                        ?: card.relationshipStatus
                } else {
                    card.relationshipStatus
                },
                mentalState = if (nextMood != null) card.mentalState.copy(mood = nextMood) else card.mentalState,
                relationships = card.relationships + (playerName to updatedRel),
                inventory = inv,
                memories = updatedMemories,
                lastModified = now
            )

            val attrMap = npcDelta.fields.orEmpty()
            if (attrMap.isNotEmpty()) {
                card = CharacterAttributeMapper.applyNpcAttributes(
                    card = card,
                    attributes = attrMap,
                    characterFields = fieldsContract,
                    playerName = playerName,
                    includeManual = false
                )
            }
            cardList[idx] = CharacterProfilePolicy.reconcile(
                CharacterAttributeMapper.validateCharacterCard(card, fieldsContract),
                playerName
            )
        }

        // 3. 更新主角状态
        delta.player?.let { pDelta ->
            pDelta.fields?.takeIf { it.isNotEmpty() }?.let { fieldsPatch ->
                player = CharacterAttributeMapper.applyPlayerAttributes(player, fieldsPatch, fieldsContract)
            }
            var pInv = player.inventory
            pDelta.inventory.orEmpty().forEach { op ->
                val itemName = op.item.trim()
                if (itemName.isBlank()) return@forEach
                when (op.op.trim().lowercase()) {
                    "add" -> pInv = CharacterAttributeMapper.addItems(pInv, itemName, op.qty.coerceAtLeast(1))
                    "remove" -> {
                        if (pInv.none { it.name == itemName }) {
                            issues += ApplyIssue(
                                ApplyIssueLevel.INFO,
                                "player_inventory:$itemName",
                                "主角背包没有该物品，忽略移除"
                            )
                        } else {
                            pInv = CharacterAttributeMapper.removeItems(pInv, itemName, op.qty.coerceAtLeast(1))
                        }
                    }
                    else -> issues += ApplyIssue(ApplyIssueLevel.WARN, "player_inventory", "未知库存操作：${op.op}")
                }
            }
            if (pInv != player.inventory) {
                player = player.copy(inventory = pInv)
            }
            val relPatches = pDelta.relationships.orEmpty()
                .mapNotNull { op ->
                    val targetName = op.name.trim()
                    val stage = op.stage.trim()
                    if (targetName.isBlank() || stage.isBlank()) return@mapNotNull null
                    val resolvedName = findCardIndex(targetName)?.let { cardList[it].name } ?: targetName
                    resolvedName to RelationshipStatePatch(stage = stage)
                }
                .toMap()
            if (relPatches.isNotEmpty()) {
                player = player.copy(
                    relationships = PlayerStatePatchPolicy.mergeRelationships(player.relationships, relPatches)
                )
            }
        }

        // 4. 物品转移（复用 InventoryTransferPolicy.prepare 计算）
        delta.transfers.orEmpty().forEach { transfer ->
            val itemName = transfer.item.trim()
            val fromName = transfer.from.trim()
            val toName = transfer.to.trim()
            if (itemName.isBlank() || fromName.isBlank() || toName.isBlank()) return@forEach

            val sourceIsPlayer = isPlayerAlias(fromName)
            val targetIsPlayer = isPlayerAlias(toName)
            val sourceIdx = if (sourceIsPlayer) null else findCardIndex(fromName)
            val targetIdx = if (targetIsPlayer) null else findCardIndex(toName)
            if ((!sourceIsPlayer && sourceIdx == null) || (!targetIsPlayer && targetIdx == null)) {
                issues += ApplyIssue(
                    ApplyIssueLevel.WARN,
                    "transfer:$itemName",
                    "物品转移参与者无法解析：$fromName -> $toName"
                )
                return@forEach
            }

            val sourceKey = if (sourceIsPlayer) "player" else cardKey(cardList[sourceIdx!!])
            val targetKey = if (targetIsPlayer) "player" else cardKey(cardList[targetIdx!!])
            val sourceInv = if (sourceIsPlayer) player.inventory else cardList[sourceIdx!!].inventory
            val targetInv = if (targetIsPlayer) player.inventory else cardList[targetIdx!!].inventory

            val decision = InventoryTransferPolicy.prepare(
                round = targetRound,
                itemName = itemName,
                quantity = transfer.qty.coerceAtLeast(1),
                sourceKey = sourceKey,
                targetKey = targetKey,
                sourceInventory = sourceInv,
                targetInventory = targetInv
            )
            val plan = decision.plan
            if (plan == null) {
                issues += ApplyIssue(
                    ApplyIssueLevel.WARN,
                    "transfer:$itemName",
                    "物品转移被跳过：${decision.failure ?: "UNKNOWN"}"
                )
                return@forEach
            }
            if (plan.writeSource) {
                if (sourceIsPlayer) {
                    player = player.copy(inventory = plan.sourceInventory)
                } else {
                    val idx = sourceIdx!!
                    cardList[idx] = cardList[idx].copy(inventory = plan.sourceInventory, lastModified = now)
                }
            }
            if (plan.writeTarget) {
                if (targetIsPlayer) {
                    player = player.copy(inventory = plan.targetInventory)
                } else {
                    val idx = targetIdx!!
                    cardList[idx] = cardList[idx].copy(inventory = plan.targetInventory, lastModified = now)
                }
            }
        }

        // 5. 待办与当前目标
        var updatedPending = story.pendingEvents
        delta.goals?.let { goals ->
            val pending = updatedPending.toMutableList()
            goals.complete.orEmpty().forEach { completed ->
                val normDone = normalizeGoal(completed)
                if (normDone.isBlank()) return@forEach
                pending.removeAll { existing ->
                    val normExisting = normalizeGoal(existing)
                    normExisting == normDone ||
                        (normDone.length >= 3 && normExisting.contains(normDone)) ||
                        (normExisting.length >= 3 && normDone.contains(normExisting))
                }
            }
            goals.update.orEmpty().forEach { update ->
                val normFrom = normalizeGoal(update.from)
                val targetText = update.to.trim()
                if (targetText.isBlank()) return@forEach
                val idx = pending.indexOfFirst { existing ->
                    val normExisting = normalizeGoal(existing)
                    normExisting == normFrom ||
                        (normFrom.length >= 3 && normExisting.contains(normFrom))
                }
                if (idx >= 0) {
                    pending[idx] = targetText
                } else if (pending.none { normalizeGoal(it) == normalizeGoal(targetText) }) {
                    pending += targetText
                }
            }
            goals.add.orEmpty().forEach { added ->
                val clean = added.trim()
                if (clean.isNotBlank() && pending.none { normalizeGoal(it) == normalizeGoal(clean) }) {
                    pending += clean
                }
            }
            updatedPending = pending
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinctBy(::normalizeGoal)
                .takeLast(MAX_PENDING_EVENTS)

            val explicitCurrent = goals.current?.trim()?.takeIf { it.isNotBlank() }
            if (explicitCurrent != null) {
                player = player.copy(
                    storyProgress = player.storyProgress.copy(currentGoal = explicitCurrent)
                )
            } else {
                val aligned = PlayerGoalPolicy.alignWithPendingEvents(player, updatedPending)
                player = if (updatedPending.isEmpty() && goals.complete.orEmpty().isNotEmpty()) {
                    aligned.copy(storyProgress = aligned.storyProgress.copy(currentGoal = ""))
                } else {
                    aligned
                }
            }
        }

        // 6. 场景与退场人物
        var updatedActiveChars = story.activeCharacters
        delta.scene?.let { scene ->
            val nextPresent = scene.present?.mapNotNull { rawName ->
                if (isPlayerAlias(rawName)) return@mapNotNull null
                findCardIndex(rawName)?.let { cardList[it].name } ?: run {
                    issues += ApplyIssue(
                        ApplyIssueLevel.INFO,
                        "scene.present:$rawName",
                        "在场人物无法对应已知角色卡，已忽略"
                    )
                    null
                }
            }?.distinct()

            story = story.copy(
                currentLocation = scene.location?.trim()?.takeIf { it.isNotBlank() } ?: story.currentLocation,
                timeOfDay = scene.timeOfDay?.trim()?.takeIf { it.isNotBlank() } ?: story.timeOfDay,
                weather = scene.weather?.trim()?.takeIf { it.isNotBlank() } ?: story.weather,
                sceneDescription = scene.description?.trim()?.takeIf { it.isNotBlank() } ?: story.sceneDescription
            )
            if (nextPresent != null) {
                updatedActiveChars = nextPresent
            } else if (createdNames.isNotEmpty()) {
                updatedActiveChars = (updatedActiveChars + createdNames).distinct()
            }
        } ?: run {
            if (createdNames.isNotEmpty()) {
                updatedActiveChars = (updatedActiveChars + createdNames).distinct()
            }
        }

        delta.departed.orEmpty().forEach { dep ->
            val rawName = dep.name.trim()
            if (rawName.isBlank()) return@forEach
            val idx = findCardIndex(rawName)
            if (idx == null) {
                issues += ApplyIssue(ApplyIssueLevel.INFO, "departed:$rawName", "退场人物无法定位，已忽略")
                return@forEach
            }
            val card = cardList[idx]
            updatedActiveChars = updatedActiveChars.filterNot { it == card.name }
            if (dep.permanent && card.status != "pool") {
                cardList[idx] = card.copy(status = "pool", lastModified = now)
            }
        }

        story = story.copy(
            activeCharacters = updatedActiveChars.distinct(),
            pendingEvents = updatedPending
        )
        if (clearLegacyPendingFlag) {
            story = StateSyncPolicy.clearPending(story)
        }

        // 7. 世界资料
        delta.world.orEmpty().forEach { wDelta ->
            val normName = WorldbookContentPolicy.normalizeName(wDelta.name)
            if (normName.isBlank()) return@forEach
            val normType = WorldbookContentPolicy.normalizeType(wDelta.type)
            val normDesc = WorldbookContentPolicy.normalizeDescription(wDelta.description)
            val normKeywords = WorldbookContentPolicy.normalizeKeywords(wDelta.keywords.orEmpty() + normName)
            val existingIdx = worldbookList.indexOfFirst { it.name.equals(normName, ignoreCase = true) }
            if (existingIdx >= 0) {
                val cur = worldbookList[existingIdx]
                val updated = WorldbookContentPolicy.normalize(
                    cur.copy(
                        description = mergeWorldbookDescription(cur.description, normDesc),
                        keywords = (cur.keywords + normKeywords).distinct(),
                        lastModified = now
                    )
                )
                worldbookList[existingIdx] = updated
            } else {
                val created = WorldbookContentPolicy.normalize(
                    WorldbookEntry(
                        id = stableId("wb", targetRound, "$normType:$normName"),
                        type = normType,
                        name = normName,
                        description = normDesc,
                        keywords = normKeywords,
                        isActive = true,
                        createdBy = "ai",
                        createdAt = now,
                        lastModified = now
                    )
                )
                worldbookList += created
            }
        }

        // 8. 剧情事件
        delta.events.orEmpty().forEach { ev ->
            val desc = ev.description.trim()
            val title = ev.title.trim()
            if (desc.isBlank() && title.isBlank()) return@forEach
            val related = ev.related.orEmpty().map(String::trim).filter(String::isNotBlank)
            val historyEntry = StatusHistory(
                time = "第${targetRound}轮",
                status = title.ifBlank { "剧情事件" },
                event = desc.ifBlank { title },
                causedBy = related.firstOrNull().orEmpty()
            )
            val targetIdx = related.firstNotNullOfOrNull { ref ->
                worldbookList.indexOfFirst { it.name.equals(ref, ignoreCase = true) }.takeIf { it >= 0 }
            }
            if (targetIdx != null) {
                val cur = worldbookList[targetIdx]
                worldbookList[targetIdx] = WorldbookContentPolicy.normalize(
                    cur.copy(
                        status = title.ifBlank { cur.status },
                        statusHistory = (cur.statusHistory + historyEntry).distinctBy {
                            listOf(it.time, it.status, it.event, it.causedBy)
                        },
                        lastModified = now
                    )
                )
            } else {
                val eventName = title.ifBlank { "第${targetRound}轮事件" }
                val existingEventIdx = worldbookList.indexOfFirst {
                    it.type == "event" && it.name.equals(eventName, ignoreCase = true)
                }
                if (existingEventIdx >= 0) {
                    val cur = worldbookList[existingEventIdx]
                    worldbookList[existingEventIdx] = WorldbookContentPolicy.normalize(
                        cur.copy(
                            description = desc.ifBlank { cur.description },
                            statusHistory = (cur.statusHistory + historyEntry).distinct(),
                            lastModified = now
                        )
                    )
                } else {
                    worldbookList += WorldbookContentPolicy.normalize(
                        WorldbookEntry(
                            id = stableId("event", targetRound, eventName),
                            type = "event",
                            name = eventName,
                            description = desc,
                            keywords = (related + eventName).distinct(),
                            status = title.ifBlank { "已记录" },
                            statusHistory = listOf(historyEntry),
                            isActive = true,
                            createdBy = "ai",
                            createdAt = now,
                            lastModified = now
                        )
                    )
                }
            }
        }

        // 9. 摘要与行动建议
        val summaryEntry = delta.summary?.trim()?.takeIf { it.isNotBlank() }?.let { text ->
            SummaryEntry(turn = targetRound, summary = text.take(180), createdAt = now)
        }
        val suggestions = delta.suggestions.orEmpty()
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .take(4)

        // 10. 计算实际变更写入清单
        val writes = buildList {
            if (player != base.player) {
                add(EntityWrite(EntityWrite.TYPE_PLAYER, "player", gson.toJsonTree(player)))
            }
            val baseCardsByKey = base.characters.associateBy(::cardKey)
            cardList.forEach { card ->
                val key = cardKey(card)
                if (baseCardsByKey[key] != card) {
                    add(EntityWrite(EntityWrite.TYPE_CHARACTER, key, gson.toJsonTree(card)))
                }
            }
            val baseWbById = base.worldbook.associateBy { it.id }
            worldbookList.forEach { wb ->
                if (baseWbById[wb.id] != wb) {
                    add(EntityWrite(EntityWrite.TYPE_WORLDBOOK, wb.id, gson.toJsonTree(wb)))
                }
            }
            if (story != base.story) {
                add(EntityWrite(EntityWrite.TYPE_STORY, "story", gson.toJsonTree(story)))
            }
            if (summaryEntry != null) {
                add(
                    EntityWrite(
                        EntityWrite.TYPE_SUMMARY,
                        targetRound.toString(),
                        gson.toJsonTree(summaryEntry)
                    )
                )
            }
        }

        val nextSnapshot = base.copy(
            story = story,
            player = player,
            characters = cardList.toList(),
            worldbook = worldbookList.toList(),
            summaries = if (summaryEntry != null) {
                (base.summaries.filterNot { it.turn == summaryEntry.turn } + summaryEntry).sortedBy { it.turn }
            } else {
                base.summaries
            }
        )
        return ApplyResult(
            snapshot = nextSnapshot,
            writes = writes,
            issues = issues,
            summary = summaryEntry,
            suggestions = suggestions
        )
    }

    internal fun appendNpcMemory(
        current: List<MemoryNote>,
        rawMemory: String?,
        turn: Int,
        favorDelta: Int
    ): List<MemoryNote> {
        val text = rawMemory?.trim().orEmpty()
        if (text.isBlank()) return current
        val normalizedNew = normalizeGoal(text)
        if (current.any { normalizeGoal(it.text) == normalizedNew }) return current

        val weight = if (kotlin.math.abs(favorDelta) >= 5 || importantMemoryKeywords.containsMatchIn(text)) {
            2
        } else {
            1
        }
        val list = (current + MemoryNote(turn = turn, text = text.take(120), weight = weight)).toMutableList()
        while (list.size > MAX_MEMORIES_PER_NPC) {
            val oldestNormalIdx = list.indexOfFirst { it.weight <= 1 }
            if (oldestNormalIdx >= 0) {
                list.removeAt(oldestNormalIdx)
            } else {
                list.removeAt(0)
            }
        }
        return list
    }

    private fun mergeWorldbookDescription(existing: String, incoming: String): String {
        val cur = existing.trim()
        val next = incoming.trim()
        if (next.isBlank()) return cur
        if (cur.isBlank()) return next
        if (cur.contains(next)) return cur
        if (next.contains(cur)) return next
        return "$cur\n$next"
    }

    private fun normalizeGoal(text: String): String =
        goalNoise.replace(text.trim(), "").lowercase()

    private fun stableId(prefix: String, round: Int, seed: String): String {
        val digest = UUID.nameUUIDFromBytes("$prefix|$round|$seed".toByteArray(StandardCharsets.UTF_8))
            .toString()
            .replace("-", "")
            .take(8)
        return "${prefix}_${round}_$digest"
    }
}
