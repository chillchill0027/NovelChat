package com.novelchat.data.storage

import com.novelchat.data.remote.AiCallStage
import com.novelchat.data.remote.model.ApiStageRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiStageRoutesCodecTest {
    private val codec = ApiStageRoutesCodec()

    @Test
    fun encodeDecode_preservesKnownAssignments() {
        val routes = ApiStageRoutes()
            .assign(AiCallStage.NARRATIVE, "本地千问")
            .assign(AiCallStage.TASK_ASSIGNMENT, "DeepSeek")

        assertEquals(routes, codec.decode(codec.encode(routes)))
    }

    @Test
    fun sanitize_dropsUnknownStagesAndMissingProfiles() {
        val routes = ApiStageRoutes(
            routes = mapOf(
                AiCallStage.NARRATIVE.storageKey to " 本地千问 ",
                AiCallStage.CHARACTER_STATE.storageKey to "已删除",
                "future_stage" to "DeepSeek"
            )
        )

        assertEquals(
            mapOf(AiCallStage.NARRATIVE.storageKey to "本地千问"),
            routes.sanitize(setOf("本地千问", "DeepSeek")).routes
        )
    }

    @Test
    fun assignBlankAndDeleteProfile_restoreDefaultRouting() {
        val routes = ApiStageRoutes()
            .assign(AiCallStage.NARRATIVE, "本地千问")
            .assign(AiCallStage.NARRATIVE_REPAIR, "本地千问")
            .assign(AiCallStage.TASK_ASSIGNMENT, "DeepSeek")
            .assign(AiCallStage.TASK_ASSIGNMENT, "")
            .withoutProfile("本地千问")

        assertTrue(routes.routes.isEmpty())
    }

    @Test
    fun decodeCorruptJson_returnsEmptyRoutes() {
        assertTrue(codec.decode("{not-json").routes.isEmpty())
    }
}
