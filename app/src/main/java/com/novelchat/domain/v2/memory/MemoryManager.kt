package com.novelchat.domain.v2.memory

import com.novelchat.data.remote.AiCallStage
import com.novelchat.data.remote.llm.LlmClient
import com.novelchat.data.remote.llm.LlmMessage
import com.novelchat.data.remote.llm.LlmPriority
import com.novelchat.data.remote.llm.LlmRequest
import com.novelchat.data.remote.llm.ResponseMode
import com.novelchat.data.repo.StorySnapshot
import com.novelchat.domain.v2.prompt.SummarizerPromptTemplate

data class ChapterBundle(
    val chapter: ChapterSummary,
    val overview: ChapterSummary? = null
)

class MemoryManager(
    private val llmClient: LlmClient
) {

    fun chapterDue(snapshot: StorySnapshot, targetToRound: Int): Boolean {
        val lastToTurn = snapshot.chapters.maxOfOrNull { it.toTurn } ?: 0
        return targetToRound - lastToTurn >= CHAPTER_INTERVAL
    }

    suspend fun summarizeChapterIfDue(
        snapshot: StorySnapshot,
        targetToRound: Int,
        createdAt: String
    ): ChapterBundle? {
        if (!chapterDue(snapshot, targetToRound)) return null

        val regularChapters = snapshot.chapters.filterNot { it.isOverview }.sortedBy { it.index }
        val existingOverview = snapshot.chapters.firstOrNull { it.isOverview }
        val lastToTurn = snapshot.chapters.maxOfOrNull { it.toTurn } ?: 0
        val fromTurn = lastToTurn + 1
        val nextIndex = (regularChapters.maxOfOrNull { it.index } ?: 0) + 1

        val summariesByTurn = snapshot.summaries.associateBy { it.turn }
        val narrativesByTurn = snapshot.narratives.associateBy { it.turn }

        val roundSummaries = (fromTurn..targetToRound).mapNotNull { turn ->
            val summary = summariesByTurn[turn]?.summary?.trim()?.takeIf { it.isNotBlank() }
                ?: narrativesByTurn[turn]?.let { entry ->
                    fallbackSnippet(entry.userInput, entry.narrative)
                }
            summary?.let { turn to it }
        }
        if (roundSummaries.isEmpty()) return null

        val previousText = regularChapters.lastOrNull()?.text
            ?: existingOverview?.text
            ?: ""
        val lastNarrativeTail = narrativesByTurn[targetToRound]
            ?.narrative
            ?.trim()
            ?.takeLast(800)
            .orEmpty()

        val chapterCompletion = llmClient.complete(
            LlmRequest(
                stage = AiCallStage.SUMMARIZER,
                messages = listOf(
                    LlmMessage("system", SummarizerPromptTemplate.chapterSystem()),
                    LlmMessage(
                        "user",
                        SummarizerPromptTemplate.chapterUser(
                            previousChapterText = previousText,
                            roundSummaries = roundSummaries,
                            lastNarrativeTail = lastNarrativeTail
                        )
                    )
                ),
                temperature = 0.3f,
                maxTokens = 600,
                responseMode = ResponseMode.TEXT,
                priority = LlmPriority.BACKGROUND
            )
        )
        val newChapter = ChapterSummary(
            index = nextIndex,
            fromTurn = fromTurn,
            toTurn = targetToRound,
            text = chapterCompletion.text.trim(),
            isOverview = false,
            createdAt = createdAt
        )

        val uncoveredRegular = (regularChapters + newChapter)
            .filter { it.toTurn > (existingOverview?.toTurn ?: 0) }
            .sortedBy { it.index }
        val newOverview = if (uncoveredRegular.size > MAX_REGULAR_CHAPTERS_WITHOUT_OVERVIEW) {
            val toMerge = uncoveredRegular.dropLast(KEEP_RECENT_CHAPTERS)
            val overviewCompletion = llmClient.complete(
                LlmRequest(
                    stage = AiCallStage.SUMMARIZER,
                    messages = listOf(
                        LlmMessage("system", SummarizerPromptTemplate.overviewSystem()),
                        LlmMessage(
                            "user",
                            SummarizerPromptTemplate.overviewUser(
                                existingOverview = existingOverview?.text.orEmpty(),
                                chaptersToMerge = toMerge
                            )
                        )
                    ),
                    temperature = 0.3f,
                    maxTokens = 800,
                    responseMode = ResponseMode.TEXT,
                    priority = LlmPriority.BACKGROUND
                )
            )
            ChapterSummary(
                index = 0,
                fromTurn = 1,
                toTurn = toMerge.maxOf { it.toTurn },
                text = overviewCompletion.text.trim(),
                isOverview = true,
                createdAt = createdAt
            )
        } else {
            null
        }

        return ChapterBundle(chapter = newChapter, overview = newOverview)
    }

    private fun fallbackSnippet(userInput: String, narrative: String): String {
        val clean = narrative.trim().replace(Regex("""\s+"""), " ")
        val head = clean.take(150)
        val tail = if (clean.length > 200) "……" + clean.takeLast(150) else ""
        return buildString {
            if (userInput.isNotBlank()) append("行动：${userInput.trim().take(60)}；")
            append(head)
            append(tail)
        }
    }

    companion object {
        const val CHAPTER_INTERVAL = 10
        const val MAX_REGULAR_CHAPTERS_WITHOUT_OVERVIEW = 6
        const val KEEP_RECENT_CHAPTERS = 4
    }
}
