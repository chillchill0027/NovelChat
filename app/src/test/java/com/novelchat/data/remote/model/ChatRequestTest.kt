package com.novelchat.data.remote.model

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRequestTest {

    private val gson = Gson()

    @Test
    fun responseFormat_serializesDeepSeekJsonMode() {
        val request = ChatRequest(
            model = "deepseek-v4-flash",
            messages = listOf(ChatMessage(role = "user", content = "输出 JSON")),
            stream = false,
            responseFormat = ResponseFormat()
        )

        val json = gson.toJson(request)

        assertTrue(json.contains("\"response_format\":{\"type\":\"json_object\"}"))
    }

    @Test
    fun responseFormat_isOmittedOutsideJsonMode() {
        val request = ChatRequest(
            model = "test-model",
            messages = listOf(ChatMessage(role = "user", content = "回复 OK")),
            stream = false
        )

        assertFalse(gson.toJson(request).contains("response_format"))
    }

    @Test
    fun reasoningEffort_serializesOnlyWhenConfigured() {
        val controlled = ChatRequest(
            model = "gemma4",
            messages = listOf(ChatMessage(role = "user", content = "回复 OK")),
            stream = true,
            reasoningEffort = "none"
        )
        val regular = controlled.copy(reasoningEffort = null)

        assertTrue(gson.toJson(controlled).contains("\"reasoning_effort\":\"none\""))
        assertFalse(gson.toJson(regular).contains("reasoning_effort"))
    }

    @Test
    fun responseFormat_serializesLocalJsonSchema() {
        val schema = JsonObject().apply {
            addProperty("type", "object")
        }
        val request = ChatRequest(
            model = "gemma4",
            messages = listOf(ChatMessage(role = "user", content = "输出 JSON")),
            stream = false,
            responseFormat = ResponseFormat(
                type = "json_schema",
                jsonSchema = JsonSchemaFormat(
                    name = "structured_response",
                    strict = false,
                    schema = schema
                )
            )
        )
        val json = gson.toJson(request)

        assertTrue(json.contains("\"type\":\"json_schema\""))
        assertTrue(json.contains("\"name\":\"structured_response\""))
        assertTrue(json.contains("\"schema\":{\"type\":\"object\"}"))
    }
}
