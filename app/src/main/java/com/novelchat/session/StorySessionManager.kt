package com.novelchat.session

import com.novelchat.data.remote.AiCallStage
import com.novelchat.data.remote.AiService
import com.novelchat.data.remote.llm.LlmClient
import com.novelchat.data.remote.llm.LlmMetrics
import com.novelchat.data.repo.StoryRepository
import com.novelchat.domain.v2.memory.MemoryManager
import com.novelchat.domain.v2.narrator.NarratorAgent
import com.novelchat.domain.v2.round.RoundOrchestrator
import com.novelchat.domain.v2.state.StateKeeperAgent
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ConcurrentHashMap

class StorySessionManager(
    private val applicationScope: CoroutineScope,
    private val aiService: AiService,
    private val llmClient: LlmClient,
    val storyRepository: StoryRepository,
    val llmMetrics: LlmMetrics
) {
    private val sessions = ConcurrentHashMap<Long, StorySession>()

    fun session(storyId: Long): StorySession {
        require(storyId > 0L) { "无效故事编号" }
        return sessions.computeIfAbsent(storyId) {
            StorySession(
                storyId = storyId,
                scope = applicationScope,
                repo = storyRepository,
                orchestrator = RoundOrchestrator(
                    repo = storyRepository,
                    narrator = NarratorAgent(llmClient),
                    metrics = llmMetrics
                ),
                stateKeeper = StateKeeperAgent(llmClient),
                memoryManager = MemoryManager(llmClient),
                isLocalNarrative = { aiService.isLocalEndpointConfigured(AiCallStage.NARRATIVE) },
                isLocalStateKeeper = { aiService.isLocalEndpointConfigured(AiCallStage.STATE_KEEPER) }
            )
        }
    }

    suspend fun closeSession(storyId: Long) {
        val removed = sessions.remove(storyId)
        removed?.close()
        storyRepository.invalidate(storyId)
    }
}
