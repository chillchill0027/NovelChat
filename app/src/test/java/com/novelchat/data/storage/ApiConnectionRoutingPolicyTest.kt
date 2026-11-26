package com.novelchat.data.storage

import com.novelchat.data.remote.AiCallStage
import com.novelchat.data.remote.model.ApiProfile
import com.novelchat.data.remote.model.ApiStageRoutes
import org.junit.Assert.assertEquals
import org.junit.Test

class ApiConnectionRoutingPolicyTest {
    private val default = ApiProfile(
        name = "DeepSeek",
        baseUrl = "https://api.deepseek.com",
        apiKey = "secret",
        model = "deepseek-v4-flash"
    )
    private val qwen = ApiProfile(
        name = "本地千问",
        baseUrl = "http://192.168.1.20:1234",
        apiKey = "",
        model = "qwen"
    )
    private val stateModel = ApiProfile(
        name = "状态模型",
        baseUrl = "https://example.com",
        apiKey = "state-secret",
        model = "state-model"
    )

    @Test
    fun resolve_usesDifferentProfilesForDifferentStages() {
        val routes = ApiStageRoutes()
            .assign(AiCallStage.NARRATIVE, qwen.name)
            .assign(AiCallStage.TASK_ASSIGNMENT, stateModel.name)

        assertEquals(
            qwen,
            ApiConnectionRoutingPolicy.resolve(
                default,
                listOf(qwen, stateModel),
                routes,
                AiCallStage.NARRATIVE
            )
        )
        assertEquals(
            stateModel,
            ApiConnectionRoutingPolicy.resolve(
                default,
                listOf(qwen, stateModel),
                routes,
                AiCallStage.TASK_ASSIGNMENT
            )
        )
    }

    @Test
    fun resolve_unassignedMissingAndDefaultStagesFallBack() {
        val routes = ApiStageRoutes()
            .assign(AiCallStage.NARRATIVE, "已删除配置")

        assertEquals(
            default,
            ApiConnectionRoutingPolicy.resolve(
                default,
                listOf(qwen),
                routes,
                AiCallStage.NARRATIVE
            )
        )
        assertEquals(
            default,
            ApiConnectionRoutingPolicy.resolve(
                default,
                listOf(qwen),
                routes,
                AiCallStage.CHARACTER_SETUP
            )
        )
        assertEquals(
            default,
            ApiConnectionRoutingPolicy.resolve(default, listOf(qwen), routes, null)
        )
    }

    @Test
    fun resolve_v2StateKeeperAndSummarizerFallBackAlongChain() {
        val taskRoutes = ApiStageRoutes().assign(AiCallStage.TASK_ASSIGNMENT, stateModel.name)
        assertEquals(
            stateModel,
            ApiConnectionRoutingPolicy.resolve(default, listOf(qwen, stateModel), taskRoutes, AiCallStage.STATE_KEEPER)
        )
        assertEquals(
            stateModel,
            ApiConnectionRoutingPolicy.resolve(default, listOf(qwen, stateModel), taskRoutes, AiCallStage.SUMMARIZER)
        )

        val keeperRoutes = taskRoutes.assign(AiCallStage.STATE_KEEPER, qwen.name)
        assertEquals(
            qwen,
            ApiConnectionRoutingPolicy.resolve(default, listOf(qwen, stateModel), keeperRoutes, AiCallStage.STATE_KEEPER)
        )
        assertEquals(
            qwen,
            ApiConnectionRoutingPolicy.resolve(default, listOf(qwen, stateModel), keeperRoutes, AiCallStage.SUMMARIZER)
        )
    }
}
