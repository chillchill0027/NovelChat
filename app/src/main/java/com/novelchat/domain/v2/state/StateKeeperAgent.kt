package com.novelchat.domain.v2.state

import com.novelchat.data.remote.AiCallStage
import com.novelchat.data.remote.llm.LlmClient
import com.novelchat.data.remote.llm.LlmMessage
import com.novelchat.data.remote.llm.LlmPriority
import com.novelchat.data.remote.llm.LlmRequest
import com.novelchat.data.remote.llm.ResponseMode
import com.novelchat.data.repo.StorySnapshot
import com.novelchat.domain.v2.prompt.StateKeeperPromptTemplate

class StateKeeperAgent(
    private val llmClient: LlmClient,
    private val parser: StateDeltaParser = StateDeltaParser()
) {

    suspend fun request(
        snapshot: StorySnapshot,
        task: SyncTask,
        isLocalEndpoint: Boolean
    ): StateDelta {
        val systemText = StateKeeperPromptTemplate.system(snapshot.world, compact = isLocalEndpoint)
        val userText = StateKeeperPromptTemplate.user(snapshot, task)
        val responseMode = if (isLocalEndpoint) ResponseMode.JSON_SCHEMA else ResponseMode.JSON_OBJECT
        val maxTokens = if (isLocalEndpoint) 1_000 else 1_800

        var lastError: StateSyncParseException? = null
        repeat(2) { attempt ->
            val effectiveUser = if (attempt == 0) {
                userText
            } else {
                "$userText\n\n上次输出不是合法 JSON，请只输出一个合法 JSON 对象。"
            }
            val completion = llmClient.complete(
                LlmRequest(
                    stage = AiCallStage.STATE_KEEPER,
                    messages = listOf(
                        LlmMessage("system", systemText),
                        LlmMessage("user", effectiveUser)
                    ),
                    temperature = if (attempt == 0) 0.2f else 0.0f,
                    maxTokens = maxTokens,
                    responseMode = responseMode,
                    jsonSchema = if (isLocalEndpoint) StateKeeperSchemas.compactSchema else null,
                    priority = LlmPriority.BACKGROUND
                )
            )
            try {
                return parser.parse(completion.text)
            } catch (e: StateSyncParseException) {
                lastError = e
            }
        }
        throw lastError ?: StateSyncParseException("StateKeeper 解析失败")
    }
}
