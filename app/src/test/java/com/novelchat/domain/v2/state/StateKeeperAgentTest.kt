package com.novelchat.domain.v2.state

import com.novelchat.data.model.NarrationSettings
import com.novelchat.data.model.NarrativeEntry
import com.novelchat.data.model.PlayerState
import com.novelchat.data.model.StoryState
import com.novelchat.data.model.WorldLore
import com.novelchat.data.model.WorldSetting
import com.novelchat.domain.v2.prompt.StateKeeperPromptTemplate
import com.novelchat.data.remote.llm.LlmClient
import com.novelchat.data.remote.llm.LlmCompletion
import com.novelchat.data.remote.llm.LlmRequest
import com.novelchat.data.remote.llm.LlmStreamEvent
import com.novelchat.data.remote.llm.ResponseMode
import com.novelchat.data.repo.StorySnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StateKeeperAgentTest {

    private class FakeLlmClient(private val outputs: MutableList<String>) : LlmClient {
        val requests = mutableListOf<LlmRequest>()
        override fun stream(request: LlmRequest): Flow<LlmStreamEvent> = emptyFlow()
        override suspend fun complete(request: LlmRequest): LlmCompletion {
            requests += request
            return LlmCompletion(text = outputs.removeAt(0))
        }
    }

    @Test
    fun request_retriesOnceOnParseFailureAndUsesSchemaOnLocalEndpoint() = runBlocking {
        val client = FakeLlmClient(
            mutableListOf(
                "不是JSON",
                """{"summary":"林克在便利店买了矿泉水"}"""
            )
        )
        val agent = StateKeeperAgent(client)
        val snap = StorySnapshot(
            storyId = 1L,
            world = WorldSetting(name = "海临市"),
            story = StoryState(currentRound = 1, currentLocation = "云桥里"),
            player = PlayerState(name = "林克"),
            characters = emptyList(),
            worldbook = emptyList(),
            narratives = listOf(NarrativeEntry(turn = 1, userInput = "买水", narrative = "买了水")),
            summaries = emptyList(),
            chapters = emptyList(),
            narration = NarrationSettings()
        )

        val delta = agent.request(snap, SyncTask(1L, listOf(1)), isLocalEndpoint = true)

        assertEquals("林克在便利店买了矿泉水", delta.summary)
        assertEquals(2, client.requests.size)
        assertEquals(ResponseMode.JSON_SCHEMA, client.requests.first().responseMode)
        assertEquals(1_000, client.requests.first().maxTokens)
    }

    @Test
    fun stateKeeperSystemPrompt_injectsWorldSpecialStatusRules() {
        val world = WorldSetting(
            name = "神雕侠侣 · 终南烟雨",
            lore = WorldLore(
                rules = listOf("【三次引流→自创江湖】偏离主线最多引流 3 次并同步更新 story_mode 与 destiny_nudges。")
            )
        )

        val prompt = StateKeeperPromptTemplate.system(world, compact = false)

        assertTrue(prompt.contains("【世界特殊状态规则（必须同步更新对应字段）】"))
        assertTrue(prompt.contains("story_mode"))
        assertTrue(prompt.contains("destiny_nudges"))
    }
}
