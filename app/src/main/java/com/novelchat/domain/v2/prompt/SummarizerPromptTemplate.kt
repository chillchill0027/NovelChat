package com.novelchat.domain.v2.prompt

import com.novelchat.domain.v2.memory.ChapterSummary

object SummarizerPromptTemplate {

    fun chapterSystem(): String =
        "你在为一部长篇互动小说写章节梗概。要写清楚：发生了哪些关键事件，人物关系有什么变化，留下了哪些悬念和承诺。只写事实，不评价，200-300 字。"

    fun chapterUser(
        previousChapterText: String,
        roundSummaries: List<Pair<Int, String>>,
        lastNarrativeTail: String
    ): String = buildString {
        appendLine("【上一章梗概】")
        appendLine(previousChapterText.ifBlank { "无" })
        appendLine()
        appendLine("【本章各回合摘要】")
        roundSummaries.forEach { (turn, summary) ->
            appendLine("第${turn}轮：$summary")
        }
        if (lastNarrativeTail.isNotBlank()) {
            appendLine()
            appendLine("【本章最后一轮正文节选】")
            append(lastNarrativeTail)
        }
    }

    fun overviewSystem(): String =
        "你在为一部长篇互动小说合并早期章节梗概。必须保留：主线推进节点、长期人物关系变化、关键物品去向、未解决的承诺或悬念。只写事实，不超过 600 字。"

    fun overviewUser(
        existingOverview: String,
        chaptersToMerge: List<ChapterSummary>
    ): String = buildString {
        if (existingOverview.isNotBlank()) {
            appendLine("【已有前情总览】")
            appendLine(existingOverview)
            appendLine()
        }
        appendLine("【待合并的早期章节】")
        chaptersToMerge.forEach { chapter ->
            appendLine("第${chapter.fromTurn}-${chapter.toTurn}轮：${chapter.text}")
        }
    }
}
