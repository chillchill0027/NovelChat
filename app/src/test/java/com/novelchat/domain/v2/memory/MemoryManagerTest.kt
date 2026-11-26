package com.novelchat.domain.v2.memory

import com.novelchat.data.model.NarrationSettings
import com.novelchat.data.model.NarrativeEntry
import com.novelchat.data.model.PlayerState
import com.novelchat.data.model.StoryState
import com.novelchat.data.model.SummaryEntry
import com.novelchat.data.model.WorldSetting
import com.novelchat.data.remote.llm.LlmClient
import com.novelchat.data.remote.llm.LlmCompletion
import com.novelchat.data.remote.llm.LlmRequest
import com.novelchat.data.remote.llm.LlmStreamEvent
import com.novelchat.data.repo.StorySnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryManagerTest {

    private class FakeLlmClient(private val responses: MutableList<String>) : LlmClient {
        val requests = mutableListOf<LlmRequest>()
        override fun stream(request: LlmRequest): Flow<LlmStreamEvent> = emptyFlow()
        override suspend fun complete(request: LlmRequest): LlmCompletion {
            requests += request
            return LlmCompletion(text = responses.removeAt(0))
        }
    }

    private fun snapshotWithRounds(
        rounds: Int,
        chapters: List<ChapterSummary> = emptyList()
    ): StorySnapshot = StorySnapshot(
        storyId = 1L,
        world = WorldSetting(name = "海临市"),
        story = StoryState(currentRound = rounds),
        player = PlayerState(name = "林克"),
        characters = emptyList(),
        worldbook = emptyList(),
        narratives = (1..rounds).map {
            NarrativeEntry(turn = it, userInput = "行动$it", narrative = "第${it}轮发生的故事正文。")
        },
        summaries = (1 until rounds).map {
            SummaryEntry(turn = it, summary = "第${it}轮摘要")
        },
        chapters = chapters,
        narration = NarrationSettings()
    )

    @Test
    fun chapterDue_triggersAtTenRoundsAndFallsBackToNarrativeForLastTurn() = runBlocking {
        val client = FakeLlmClient(mutableListOf("第1-10轮章节梗概。"))
        val manager = MemoryManager(client)

        val round9 = snapshotWithRounds(9)
        assertFalse(manager.chapterDue(round9, 9))
        assertNull(manager.summarizeChapterIfDue(round9, 9, "now"))

        val round10 = snapshotWithRounds(10)
        assertTrue(manager.chapterDue(round10, 10))
        val bundle = manager.summarizeChapterIfDue(round10, 10, "2026-11-26T12:00:00")
        assertNotNull(bundle)
        assertEquals(1, bundle!!.chapter.index)
        assertEquals(1, bundle.chapter.fromTurn)
        assertEquals(10, bundle.chapter.toTurn)
        assertNull(bundle.overview)
        // 验证第10轮虽缺SummaryEntry，也从正文兜底拼进了提示词
        assertTrue(client.requests.single().messages.last().content.contains("第10轮"))
    }

    @Test
    fun summarizeChapterIfDue_createsOverviewWhenUncoveredChaptersExceedSix() = runBlocking {
        val existingChapters = (1..6).map { idx ->
            ChapterSummary(
                index = idx,
                fromTurn = (idx - 1) * 10 + 1,
                toTurn = idx * 10,
                text = "第${idx}章内容"
            )
        }
        val client = FakeLlmClient(mutableListOf("第7章内容", "前3章合并总览"))
        val manager = MemoryManager(client)

        val snap = snapshotWithRounds(70, chapters = existingChapters)
        val bundle = manager.summarizeChapterIfDue(snap, 70, "2026-11-26T12:00:00")

        assertNotNull(bundle)
        assertEquals(7, bundle!!.chapter.index)
        assertNotNull(bundle.overview)
        assertTrue(bundle.overview!!.isOverview)
        assertEquals(30, bundle.overview!!.toTurn)
        assertEquals("前3章合并总览", bundle.overview!!.text)
    }
}
