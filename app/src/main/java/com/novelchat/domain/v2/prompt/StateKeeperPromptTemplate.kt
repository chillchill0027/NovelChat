package com.novelchat.domain.v2.prompt

import com.novelchat.data.model.CharacterFieldDefinition
import com.novelchat.data.model.CharacterFieldPolicy
import com.novelchat.data.model.WorldSetting
import com.novelchat.data.repo.StorySnapshot
import com.novelchat.domain.engine.CharacterContextPolicy
import com.novelchat.domain.v2.state.SyncTask

object StateKeeperPromptTemplate {

    fun system(world: WorldSetting, compact: Boolean): String = buildString {
        appendLine("你是互动小说的记录员。阅读本轮剧情，把它对人物和世界造成的变化记成一份 JSON。")
        appendLine()
        appendLine("规则：")
        appendLine("1. 只记录正文里实际发生的变化。不确定的就不写。所有字段都可以省略；什么都没变就输出 {\"summary\": \"…\"}。")
        appendLine("2. 人物一律使用【已知人物】里的原名。正文里首次出场、和主角有互动的人放进 new_npcs，并给出名字和身份。")
        appendLine("3. 角色字段写本轮结束时的最终值，必须符合【字段说明】中的类型和范围。update_mode 为 manual 的字段不要写。若存在【世界特殊状态规则】涉及的追踪字段（如命线、引流次数），每轮须按规则准确更新。")
        appendLine("4. 好感、信任写变化量 favor_delta 和 trust_delta（通常在 -10 到 +10 之间）。")
        appendLine("5. 待办：彻底完成的放进 complete；有进展但没完成的用 update 改写；新出现的放进 add。")
        appendLine("6. 角色之间交接物品写 transfers；从环境中获得或消耗的物品写 player.inventory 或 npcs[].inventory。同一件事只记一次。")
        appendLine("7. scene.present 只写正文结尾时和主角在同一现场的人（打电话、发消息的另一方不算）。")
        appendLine("8. npcs[].memory：这个角色以后会记得的一件事，一句话；没有就不写。")
        appendLine("9. suggestions：给出 3 个玩家接下来可能想做的事，每条 15 字以内，贴合当前局面，彼此不同。")
        appendLine("10. 只输出 JSON。")
        val worldRules = world.lore.rules
            .map { it.trim().take(140) }
            .filter(String::isNotBlank)
            .take(6)
        if (worldRules.isNotEmpty()) {
            appendLine()
            appendLine("【世界特殊状态规则（必须同步更新对应字段）】")
            worldRules.forEach { rule -> appendLine("- $rule") }
        }
        appendLine()
        appendLine("【字段说明】")
        CharacterFieldPolicy.effectiveDefinitions(world.characterFields)
            .filter { it.key != "name" && it.display != CharacterFieldDefinition.DISPLAY_HIDDEN }
            .forEach { field ->
                val constraint = when (field.valueType) {
                    CharacterFieldDefinition.TYPE_NUMBER -> "${field.minValue}~${field.maxValue}${field.unit}"
                    CharacterFieldDefinition.TYPE_CHOICE -> if (field.options.isNotEmpty()) {
                        "选项:${field.options.joinToString("/")}"
                    } else {
                        "文本"
                    }
                    CharacterFieldDefinition.TYPE_LIST -> "列表(顿号分隔)"
                    else -> "文本"
                }
                val desc = field.description.trim().take(60)
                appendLine(
                    "${field.key}｜${field.label}｜$constraint｜${field.appliesTo}｜${field.updateMode}" +
                        if (desc.isNotBlank()) "｜$desc" else ""
                )
            }
        appendLine()
        appendLine("【输出格式示例】")
        append(if (compact) COMPACT_EXAMPLE else FULL_EXAMPLE)
    }

    fun user(snapshot: StorySnapshot, task: SyncTask): String = buildString {
        val story = snapshot.story
        val player = snapshot.player
        val targetRounds = task.rounds.sorted()
        val lastRound = targetRounds.lastOrNull() ?: story.currentRound
        val targetNarratives = snapshot.narratives.filter { it.turn in targetRounds }.sortedBy { it.turn }
        val latestEntry = targetNarratives.lastOrNull()
        val earlierEntries = if (targetNarratives.size > 1) {
            targetNarratives.dropLast(1).takeLast(3)
        } else {
            emptyList()
        }

        appendLine("【当前状态】")
        val presentText = story.activeCharacters.takeIf { it.isNotEmpty() }?.joinToString("、") ?: "无"
        appendLine("场景：${story.currentLocation.ifBlank { "未知" }}｜${story.timeOfDay}｜${story.weather}；在场：$presentText")
        if (story.pendingEvents.isEmpty()) {
            appendLine("待办：无")
        } else {
            appendLine("待办：")
            story.pendingEvents.forEachIndexed { idx, event ->
                appendLine("${idx + 1}. $event")
            }
        }

        val dynamicPairs = CharacterFieldPolicy.effectiveDefinitions(snapshot.world.characterFields)
            .filter {
                it.updateMode == CharacterFieldDefinition.UPDATE_DYNAMIC &&
                    it.display != CharacterFieldDefinition.DISPLAY_HIDDEN &&
                    CharacterFieldPolicy.appliesTo(it, CharacterFieldDefinition.TARGET_PLAYER)
            }
            .mapNotNull { field ->
                val v = CharacterFieldPolicy.valueFor(field, player) ?: return@mapNotNull null
                "${field.key}=$v"
            }
            .take(10)
            .joinToString("，")
        val invText = if (player.inventory.isEmpty()) {
            "无"
        } else {
            player.inventory.take(12).joinToString("、") { item ->
                if (item.quantity > 1) "${item.name}×${item.quantity}" else item.name
            }
        }
        appendLine("${player.name.ifBlank { "主角" }}：${dynamicPairs.ifBlank { "状态正常" }}；物品：$invText")

        val relevantChars = CharacterContextPolicy.selectForStateSync(
            allCharacters = snapshot.characters,
            narrativeCharacters = snapshot.characters.filter { it.name in story.activeCharacters },
            currentNarrative = latestEntry?.narrative.orEmpty(),
            recoveryNarratives = earlierEntries
        )
        if (relevantChars.isNotEmpty()) {
            appendLine()
            appendLine("【已知人物】")
            relevantChars.forEach { card ->
                val rel = card.relationships[player.name]
                val stage = rel?.stage?.takeIf { it.isNotBlank() } ?: card.relationshipStatus
                val favor = rel?.favor ?: card.favor
                val trust = rel?.trust ?: 0
                val mood = card.emotion.ifBlank { card.mentalState.mood }
                appendLine("${card.name}｜${card.occupation.ifBlank { "未知" }}｜$stage｜好感$favor/信任$trust｜$mood")
            }
        }

        if (earlierEntries.isNotEmpty()) {
            appendLine()
            appendLine("【之前未记录的剧情】")
            earlierEntries.forEach { entry ->
                appendLine("第${entry.turn}轮 行动：${entry.userInput.take(240)}")
                appendLine(entry.narrative.take(900))
                appendLine()
            }
        }

        appendLine()
        appendLine("【本轮】（第${lastRound}轮）")
        if (latestEntry != null) {
            appendLine("行动：${latestEntry.userInput}")
            appendLine("正文：")
            append(latestEntry.narrative)
        }
    }

    private const val COMPACT_EXAMPLE = """{"summary":"一句话说明发生了什么","scene":{"location":"地点","time_of_day":"时间","weather":"天气","present":["在场人名"],"description":"一句话结尾画面"},"goals":{"complete":["已完成待办"],"add":["新待办"],"update":[{"from":"旧待办","to":"更新后待办"}],"current":"当前主要目标"},"player":{"fields":{"stamina":"80"},"inventory":[{"op":"add","item":"物品","qty":1}]},"npcs":[{"name":"已知人名","emotion":"情绪","favor_delta":3,"memory":"一句重要记忆"}],"new_npcs":[{"name":"新人真名","identity":"身份","relationship_to_player":"关系"}],"suggestions":["下一步建议1","建议2","建议3"]}"""

    private const val FULL_EXAMPLE = """{"summary":"一句话说明发生了什么","scene":{"location":"地点","time_of_day":"时间","weather":"天气","present":["在场人名"],"description":"一句话结尾画面"},"goals":{"complete":["已完成待办"],"add":["新待办"],"update":[{"from":"旧待办","to":"更新后待办"}],"current":"当前主要目标"},"player":{"fields":{"stamina":"80","stress":"中"},"inventory":[{"op":"add","item":"物品","qty":1}],"relationships":[{"name":"人名","stage":"关系阶段"}]},"npcs":[{"name":"已知人名","emotion":"情绪","favor_delta":3,"trust_delta":2,"relationship_stage":"关系阶段","fields":{"stress":"低"},"memory":"一句重要记忆","inventory":[{"op":"remove","item":"物品","qty":1}]}],"new_npcs":[{"name":"新人真名","identity":"身份","relationship_to_player":"关系","gender":"女","age":24,"personality":"性格","appearance":"外貌","speech_style":"说话方式","fields":{}}],"departed":[{"name":"离开人名","permanent":false}],"transfers":[{"item":"物品","qty":1,"from":"给出方","to":"接收方"}],"world":[{"name":"地点或势力名","type":"location","description":"新事实","keywords":["关键词"]}],"events":[{"title":"事件标题","description":"发生了什么","related":["相关人物或地点"]}],"suggestions":["下一步建议1","建议2","建议3"]}"""
}
