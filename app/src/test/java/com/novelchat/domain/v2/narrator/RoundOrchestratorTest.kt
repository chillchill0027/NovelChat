package com.novelchat.domain.v2.narrator

import com.novelchat.data.model.PlayerState
import com.novelchat.data.model.StoryState
import com.novelchat.data.model.WorldSetting
import com.novelchat.data.remote.llm.LlmClient
import com.novelchat.data.remote.llm.LlmCompletion
import com.novelchat.data.remote.llm.LlmMetrics
import com.novelchat.data.remote.llm.LlmRequest
import com.novelchat.data.remote.llm.LlmStreamEvent
import com.novelchat.data.repo.StoryRepository
import com.novelchat.data.storage.FileStorageManager
import com.novelchat.domain.v2.context.PlayerInput
import com.novelchat.domain.v2.round.RoundEvent
import com.novelchat.domain.v2.round.RoundOrchestrator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RoundOrchestratorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class ScriptedLlmClient(
        private val streamOutputs: MutableList<String>
    ) : LlmClient {
        val requests = mutableListOf<LlmRequest>()

        override fun stream(request: LlmRequest): Flow<LlmStreamEvent> = flow {
            requests += request
            val text = streamOutputs.removeAt(0)
            if (text.isNotEmpty()) {
                emit(LlmStreamEvent.Delta(text.take(20)))
                if (text.length > 20) emit(LlmStreamEvent.Delta(text.drop(20)))
            }
            emit(LlmStreamEvent.Finished(finishReason = "stop"))
        }

        override suspend fun complete(request: LlmRequest): LlmCompletion =
            LlmCompletion(text = "")
    }

    private suspend fun setupStory(): Pair<StoryRepository, Long> {
        val files = FileStorageManager.forDirectory(tempFolder.root)
        val id = files.createConversationStorage()
        files.saveWorldSetting(id, WorldSetting(name = "海临市", description = "测试"))
        files.savePlayerState(id, PlayerState(name = "林克"))
        files.saveStoryState(id, StoryState(currentLocation = "云桥里"))
        files.completeConversationStorage(id)
        return StoryRepository(files) to id
    }

    @Test
    fun orchestrator_retriesOnceOnShortOutputThenCommitsNarrative() = runBlocking {
        val (repo, storyId) = setupStory()
        val goodText = "林克推开铁门走进楼道，声控灯应声亮起。陈姐正在门厅整理水电单据，听见动静回过头，顺手把一张叠好的通知递过来：“正好碰到你，下周二上午小区要停水检修。”"
        val client = ScriptedLlmClient(mutableListOf("太短了", goodText))
        val metrics = LlmMetrics(logSink = {})
        val orchestrator = RoundOrchestrator(repo, NarratorAgent(client), metrics)

        val events = orchestrator.run(storyId, turn = 1, PlayerInput("下楼出门"), isLocalEndpoint = false).toList()

        assertTrue(events.any { it is RoundEvent.NarrativeReset })
        val committed = events.filterIsInstance<RoundEvent.NarrativeCommitted>().single()
        assertEquals(1, committed.turn)
        assertEquals(goodText, committed.narrative)
        assertEquals(2, client.requests.size)

        val snap = repo.snapshot(storyId)
        assertEquals(1, snap.narratives.size)
        assertEquals(1, snap.story.currentRound)
        assertEquals(1, metrics.recentRounds().size)
    }

    @Test
    fun orchestrator_capsRetriesAtOneAndEmitsFailedWhenStillEmpty() = runBlocking {
        val (repo, storyId) = setupStory()
        val client = ScriptedLlmClient(mutableListOf("", ""))
        val metrics = LlmMetrics(logSink = {})
        val orchestrator = RoundOrchestrator(repo, NarratorAgent(client), metrics)

        val events = orchestrator.run(storyId, turn = 1, PlayerInput("出门"), isLocalEndpoint = false).toList()

        assertEquals(2, client.requests.size)
        assertTrue(events.last() is RoundEvent.Failed)
        assertTrue(repo.snapshot(storyId).narratives.isEmpty())
    }
}
