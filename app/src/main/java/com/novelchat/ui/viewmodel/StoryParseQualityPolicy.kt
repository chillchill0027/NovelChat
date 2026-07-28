package com.novelchat.ui.viewmodel

import com.novelchat.data.model.CharacterNamingPolicy
import com.novelchat.data.remote.model.StoryParseResult

internal object StoryParseQualityPolicy {

    fun hasSemanticContent(result: StoryParseResult): Boolean {
        val world = result.worldSetting
        val player = result.player
        return listOf(
            world.name,
            world.genre,
            world.description,
            player.name,
            player.class_,
            player.background
        ).any { it.isNotBlank() } || result.npcs.isNotEmpty() || result.worldbook.isNotEmpty()
    }

    fun qualityIssue(result: StoryParseResult): String? {
        if (!hasSemanticContent(result)) return "没有解析出任何世界、主角或设定条目"

        val issues = buildList {
            if (result.worldSetting.name.isBlank() || result.worldSetting.description.isBlank()) {
                add("世界名称或世界描述缺失")
            }
            if (CharacterNamingPolicy.isGenericPlayerName(result.player.name)) {
                add("主角缺少具体姓名")
            }
            result.npcs.firstOrNull { CharacterNamingPolicy.isGenericNpcName(it.name) }?.let {
                add("NPC“${it.name.ifBlank { it.occupation.ifBlank { "未命名角色" } }}”仍是身份泛称")
            }
            if (result.npcs.any {
                    it.name.isNotBlank() && it.name.trim() == result.player.name.trim()
                }
            ) {
                add("主角与 NPC 不能使用同一个姓名")
            }
            val duplicateNpcName = result.npcs
                .map { it.name.trim() }
                .filter { it.isNotBlank() }
                .groupingBy { it }
                .eachCount()
                .entries
                .firstOrNull { it.value > 1 }
                ?.key
            if (duplicateNpcName != null) add("多个 NPC 使用了同一个姓名“$duplicateNpcName”")
            if (result.worldbook.isEmpty()) add("没有生成可检索的世界书条目")
        }
        return issues.joinToString("；").takeIf { it.isNotBlank() }
    }

    fun requireUsable(result: StoryParseResult): StoryParseResult {
        require(hasSemanticContent(result)) { "AI 没有返回可用的故事结构" }
        require(!CharacterNamingPolicy.isGenericPlayerName(result.player.name)) { "主角缺少具体姓名" }
        result.npcs.firstOrNull { CharacterNamingPolicy.isGenericNpcName(it.name) }?.let {
            throw IllegalArgumentException("角色“${it.name}”仍缺少具体姓名")
        }
        require(result.npcs.none {
                it.name.isNotBlank() && it.name.trim() == result.player.name.trim()
            }
        ) {
            "主角与 NPC 姓名重复"
        }
        require(
            result.npcs.map { it.name.trim() }.filter { it.isNotBlank() }.distinct().size ==
                result.npcs.count { it.name.isNotBlank() }
        ) { "NPC 姓名存在重复" }
        require(result.worldSetting.name.isNotBlank() && result.worldSetting.description.isNotBlank()) {
            "世界设定不完整"
        }
        require(result.worldbook.isNotEmpty()) { "世界书条目为空" }
        return result
    }

    fun concretePlayerName(rawName: String): String {
        return CharacterNamingPolicy.concretePlayerName(rawName)
    }
}
