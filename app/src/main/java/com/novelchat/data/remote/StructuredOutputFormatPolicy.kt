package com.novelchat.data.remote

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.novelchat.data.remote.model.JsonSchemaFormat
import com.novelchat.data.remote.model.ResponseFormat

enum class StructuredOutputKind {
    GENERIC_OBJECT,
    NARRATIVE
}

internal object StructuredOutputFormatPolicy {

    fun responseFormat(
        baseUrl: String,
        kind: StructuredOutputKind = StructuredOutputKind.GENERIC_OBJECT
    ): ResponseFormat {
        if (!ApiEndpointPolicy.isLocalEndpoint(baseUrl)) return ResponseFormat()
        val (name, schema) = when (kind) {
            StructuredOutputKind.GENERIC_OBJECT -> "structured_response" to genericObjectSchema
            StructuredOutputKind.NARRATIVE -> "novelchat_narrative" to narrativeSchema
        }
        return ResponseFormat(
            type = "json_schema",
            jsonSchema = JsonSchemaFormat(
                name = name,
                strict = kind == StructuredOutputKind.NARRATIVE,
                schema = schema
            )
        )
    }

    private val genericObjectSchema: JsonObject = objectSchema(
        properties = emptyMap(),
        required = emptyList(),
        additionalProperties = true
    )

    private val narrativeSchema: JsonObject = objectSchema(
        properties = linkedMapOf(
            "narrative" to stringSchema(),
            "summary" to stringSchema(),
            "action_coverage" to arraySchema(
                objectSchema(
                    properties = linkedMapOf(
                        "action" to stringSchema(),
                        "evidence" to stringSchema()
                    ),
                    required = listOf("action", "evidence")
                )
            ),
            "forward_hook" to objectSchema(
                properties = linkedMapOf(
                    "cause" to stringSchema(),
                    "evidence" to stringSchema()
                ),
                required = listOf("cause", "evidence")
            ),
            "ending_scene" to objectSchema(
                properties = linkedMapOf(
                    "location" to stringSchema(),
                    "present_characters" to arraySchema(stringSchema())
                ),
                required = listOf("location", "present_characters")
            ),
            "introduced_characters" to arraySchema(
                objectSchema(
                    properties = linkedMapOf(
                        "name" to stringSchema(),
                        "occupation" to stringSchema(),
                        "relationship_to_player" to stringSchema(),
                        "first_appearance" to stringSchema()
                    ),
                    required = listOf(
                        "name",
                        "occupation",
                        "relationship_to_player",
                        "first_appearance"
                    )
                )
            )
        ),
        required = listOf(
            "narrative",
            "summary",
            "action_coverage",
            "forward_hook",
            "ending_scene",
            "introduced_characters"
        )
    )

    private fun stringSchema(): JsonObject = JsonObject().apply {
        addProperty("type", "string")
    }

    private fun arraySchema(items: JsonObject): JsonObject = JsonObject().apply {
        addProperty("type", "array")
        add("items", items)
    }

    private fun objectSchema(
        properties: Map<String, JsonObject>,
        required: List<String>,
        additionalProperties: Boolean = false
    ): JsonObject = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().also { propertyObject ->
            properties.forEach(propertyObject::add)
        })
        add("required", JsonArray().also { requiredArray ->
            required.forEach(requiredArray::add)
        })
        addProperty("additionalProperties", additionalProperties)
    }
}
