package com.novelchat.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredOutputFormatPolicyTest {

    @Test
    fun responseFormat_keepsJsonObjectForRemoteEndpoints() {
        val format = StructuredOutputFormatPolicy.responseFormat(
            baseUrl = "https://api.deepseek.com/v1",
            kind = StructuredOutputKind.NARRATIVE
        )

        assertEquals("json_object", format.type)
        assertNull(format.jsonSchema)
    }

    @Test
    fun responseFormat_usesPermissiveObjectSchemaForLocalStructuredCalls() {
        val format = StructuredOutputFormatPolicy.responseFormat(
            baseUrl = "http://192.168.1.20:1234/v1"
        )
        val definition = requireNotNull(format.jsonSchema)

        assertEquals("json_schema", format.type)
        assertEquals("structured_response", definition.name)
        assertFalse(definition.strict)
        assertEquals("object", definition.schema.get("type").asString)
        assertTrue(definition.schema.get("additionalProperties").asBoolean)
    }

    @Test
    fun responseFormat_narrativeSchemaMatchesParserContract() {
        val format = StructuredOutputFormatPolicy.responseFormat(
            baseUrl = "http://127.0.0.1:1234",
            kind = StructuredOutputKind.NARRATIVE
        )
        val definition = requireNotNull(format.jsonSchema)
        val properties = definition.schema.getAsJsonObject("properties")

        assertEquals("json_schema", format.type)
        assertTrue(definition.strict)
        assertEquals(
            setOf(
                "narrative",
                "summary",
                "action_coverage",
                "forward_hook",
                "ending_scene",
                "introduced_characters"
            ),
            properties.keySet()
        )
        assertEquals(
            setOf("cause", "evidence"),
            properties.getAsJsonObject("forward_hook")
                .getAsJsonObject("properties")
                .keySet()
        )
        assertTrue(
            properties.getAsJsonObject("ending_scene")
                .getAsJsonObject("properties")
                .has("present_characters")
        )
        assertEquals(
            setOf("name", "occupation", "relationship_to_player", "first_appearance"),
            properties.getAsJsonObject("introduced_characters")
                .getAsJsonObject("items")
                .getAsJsonObject("properties")
                .keySet()
        )
    }
}
