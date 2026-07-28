package com.novelchat.domain.worldbook

import com.novelchat.data.model.NarrativeEntry
import com.novelchat.data.model.StoryState
import com.novelchat.data.model.WorldbookEntry

/**
 * 世界书关键词匹配器
 * 对齐设计文档 8.5 节：三种触发方式
 */
class WorldbookMatcher {

    /**
     * 匹配并返回所有相关的世界书条目
     *
     * @param userMessage 用户输入
     * @param storyState 当前剧情状态（含 currentLocation）
     * @param allEntries 所有活跃世界书条目
     * @param recentNarratives 最近几轮对话（用于前文提及匹配）
     * @param maxEntries 最大返回数量
     * @return 匹配的条目列表，按优先级排序（方式2 > 方式3 > 方式1）
     */
    fun match(
        userMessage: String,
        storyState: StoryState,
        allEntries: List<WorldbookEntry>,
        recentNarratives: List<NarrativeEntry>,
        maxEntries: Int = 10
    ): List<WorldbookEntry> {
        val matched = mutableSetOf<WorldbookEntry>()
        val order = mutableMapOf<WorldbookEntry, Int>() // 用于排序

        // 方式2：当前场景匹配（优先级最高）
        if (storyState.currentLocation.isNotBlank()) {
            allEntries.forEach { entry ->
                if (entry.type == "location" && (
                        entry.name == storyState.currentLocation ||
                        entry.keywords.any { it == storyState.currentLocation })) {
                    if (matched.add(entry)) {
                        order[entry] = 0
                    }
                }
            }
        }

        // 方式3：前文提及匹配
        val recentText = recentNarratives.joinToString(" ") { it.narrative }
        allEntries.forEach { entry ->
            if (entry !in matched && entry.keywords.any { keyword ->
                    recentText.contains(keyword, ignoreCase = true)
                }) {
                matched.add(entry)
                order[entry] = 1
            }
        }

        // 方式1：用户输入与当前待办关键词匹配
        val decisionText = buildString {
            append(userMessage)
            storyState.pendingEvents.forEach { event ->
                append(' ')
                append(event)
            }
        }
        allEntries.forEach { entry ->
            if (entry !in matched && entry.keywords.any { keyword ->
                    decisionText.contains(keyword, ignoreCase = true)
                }) {
                matched.add(entry)
                order[entry] = 2
            }
        }

        // 按优先级排序（方式2 > 3 > 1），然后截取上限
        return matched.sortedBy { order[it] }.take(maxEntries)
    }
}
