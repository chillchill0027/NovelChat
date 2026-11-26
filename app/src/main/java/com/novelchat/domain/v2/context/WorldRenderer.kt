package com.novelchat.domain.v2.context

import com.novelchat.data.model.WorldSetting

object WorldRenderer {

    fun prose(world: WorldSetting, maxChars: Int = 2_500): String {
        val lines = buildList {
            if (world.name.isNotBlank()) {
                val meta = listOf(world.genre, world.lore.era, world.tone)
                    .filter { it.isNotBlank() }
                    .joinToString("｜")
                add(if (meta.isNotBlank()) "${world.name}（$meta）" else world.name)
            }
            if (world.description.isNotBlank()) add(world.description.trim())
            val lore = world.lore
            addLine("背景前提", lore.premise)
            addLine("历史脉络", lore.history)
            addLine("地理空间", lore.geography)
            addLine("力量/技术规则", listOf(lore.powerSystem, world.magicLevel, world.techLevel, lore.technologyRules).filter { it.isNotBlank() }.distinct().joinToString("；"))
            addLine("社会与制度", listOf(lore.society, lore.culture, lore.law, lore.economy).filter { it.isNotBlank() }.joinToString("；"))
            if (lore.factions.isNotEmpty()) add("主要势力：${lore.factions.take(8).joinToString("、")}")
            if (lore.conflicts.isNotEmpty()) add("核心矛盾：${lore.conflicts.take(6).joinToString("；")}")
            if (lore.rules.isNotEmpty()) {
                add("世界法则：${lore.rules.take(8).joinToString("；")}")
            }
            if (world.forbidden.isNotEmpty()) {
                add("叙事禁忌：${world.forbidden.take(8).joinToString("；")}")
            }
            if (lore.custom.isNotEmpty()) {
                lore.custom.entries.take(6).forEach { (k, v) ->
                    if (k.isNotBlank() && v.isNotBlank()) add("$k：$v")
                }
            }
        }
        return clipLines(lines, maxChars)
    }

    private fun MutableList<String>.addLine(label: String, value: String) {
        val trimmed = value.trim()
        if (trimmed.isNotBlank()) add("$label：$trimmed")
    }

    private fun clipLines(lines: List<String>, maxChars: Int): String {
        if (maxChars <= 0) return ""
        val sb = StringBuilder()
        for (line in lines) {
            val nextLen = if (sb.isEmpty()) line.length else sb.length + 1 + line.length
            if (nextLen <= maxChars) {
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append(line)
            } else {
                val remaining = maxChars - sb.length - (if (sb.isEmpty()) 0 else 1)
                if (remaining >= 24) {
                    if (sb.isNotEmpty()) sb.append('\n')
                    sb.append(line.take(remaining - 1)).append('…')
                }
                break
            }
        }
        return sb.toString()
    }
}
