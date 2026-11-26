package com.novelchat.domain.v2.context

import com.novelchat.data.model.CharacterFieldDefinition
import com.novelchat.data.model.CharacterFieldPolicy
import com.novelchat.data.model.PlayerState
import com.novelchat.data.model.WorldSetting

object PlayerRenderer {

    /**
     * 静态主角档案（放入 system，不放每轮波动的动态数值，利于前缀缓存命中）。
     */
    fun profile(player: PlayerState, world: WorldSetting, maxChars: Int = 1_600): String {
        val lines = buildList {
            val basics = listOfNotNull(
                player.name.takeIf { it.isNotBlank() },
                player.gender.takeIf { it.isNotBlank() },
                player.age.takeIf { it > 0 }?.let { "${it}岁" },
                player.class_.takeIf { it.isNotBlank() },
                player.race.takeIf { it.isNotBlank() && it != "人类" }
            ).joinToString("｜")
            if (basics.isNotBlank()) add(basics)
            if (player.appearance.isNotBlank()) add("外貌：${player.appearance.trim()}")
            if (player.personality.isNotBlank()) add("性格：${player.personality.trim()}")
            if (player.background.isNotBlank()) add("背景：${player.background.trim()}")

            val lore = player.lore
            if (lore.speechStyle.isNotBlank()) add("说话习惯：${lore.speechStyle.trim()}")
            if (lore.values.isNotEmpty()) add("看重：${lore.values.take(5).joinToString("、")}")
            if (lore.goals.isNotEmpty()) add("长期目标：${lore.goals.take(5).joinToString("；")}")
            if (lore.fears.isNotEmpty()) add("忌惮：${lore.fears.take(4).joinToString("、")}")
            if (lore.weaknesses.isNotEmpty()) add("弱点：${lore.weaknesses.take(4).joinToString("、")}")
            if (lore.habits.isNotEmpty()) add("习惯：${lore.habits.take(4).joinToString("、")}")
            if (lore.boundaries.isNotEmpty()) add("底线：${lore.boundaries.take(4).joinToString("；")}")
            if (lore.abilities.isNotEmpty()) {
                add("擅长/能力：${lore.abilities.take(6).joinToString("、")}")
            } else if (player.skills.isNotEmpty()) {
                add("技能：${player.skills.take(6).joinToString("、") { it.name }}")
            }

            // 故事契约里的非 dynamic 非隐藏长期字段（若有自定义）
            val staticCustomFields = CharacterFieldPolicy.effectiveDefinitions(world.characterFields)
                .filter { field ->
                    field.updateMode != CharacterFieldDefinition.UPDATE_DYNAMIC &&
                        field.display != CharacterFieldDefinition.DISPLAY_HIDDEN &&
                        CharacterFieldPolicy.appliesTo(field, CharacterFieldDefinition.TARGET_PLAYER) &&
                        field.key !in standardProfileKeys
                }
            val renderedStatic = staticCustomFields.mapNotNull { field ->
                val raw = CharacterFieldPolicy.valueFor(field, player) ?: return@mapNotNull null
                "${field.label}：${CharacterFieldPolicy.formatValue(field, raw, compact = false)}"
            }
            if (renderedStatic.isNotEmpty()) {
                add(renderedStatic.take(6).joinToString("；"))
            }
        }
        return clipText(lines.joinToString("\n"), maxChars)
    }

    /**
     * 主角动态现状（放在末尾动态情境块，严格控制在 maxChars 内）。
     */
    fun status(player: PlayerState, world: WorldSetting, maxChars: Int = 120): String {
        val dynamicFields = CharacterFieldPolicy.effectiveDefinitions(world.characterFields)
            .filter { field ->
                field.updateMode == CharacterFieldDefinition.UPDATE_DYNAMIC &&
                    field.display != CharacterFieldDefinition.DISPLAY_HIDDEN &&
                    CharacterFieldPolicy.appliesTo(field, CharacterFieldDefinition.TARGET_PLAYER)
            }
        val parts = mutableListOf<String>()
        dynamicFields.forEach { field ->
            val raw = CharacterFieldPolicy.valueFor(field, player) ?: return@forEach
            if (isDefaultNormalStatus(field.key, raw)) return@forEach
            val formatted = CharacterFieldPolicy.formatValue(field, raw, compact = true)
            parts += "${field.label} $formatted"
        }
        if (player.physicalState.injuries.isNotEmpty()) {
            parts += "伤势：${player.physicalState.injuries.take(2).joinToString("、")}"
        }
        if (player.inventory.isNotEmpty()) {
            val items = player.inventory.take(4).joinToString("、") { item ->
                if (item.quantity > 1) "${item.name}×${item.quantity}" else item.name
            }
            parts += "随身：$items"
        }
        val summary = parts.joinToString("；").ifBlank { "状态平稳" }
        return clipText(summary, maxChars)
    }

    private fun isDefaultNormalStatus(key: String, value: String): Boolean {
        return when (key) {
            "health" -> value == "良好"
            "fatigue", "pain" -> value == "无"
            "hunger" -> value == "不饿"
            "current_thoughts", "focus" -> value.isBlank()
            else -> false
        }
    }

    private fun clipText(text: String, maxChars: Int): String {
        if (maxChars <= 0) return ""
        val trimmed = text.trim()
        return if (trimmed.length <= maxChars) trimmed else trimmed.take(maxChars - 1) + "…"
    }

    private val standardProfileKeys = setOf(
        "name", "age", "gender", "race", "occupation",
        "appearance", "personality", "background", "skills"
    )
}
