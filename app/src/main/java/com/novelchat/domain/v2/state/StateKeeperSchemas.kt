package com.novelchat.domain.v2.state

import com.google.gson.JsonArray
import com.google.gson.JsonObject

object StateKeeperSchemas {

    val compactSchema: JsonObject by lazy {
        obj(
            properties = linkedMapOf(
                "summary" to str(),
                "scene" to obj(
                    properties = linkedMapOf(
                        "location" to str(),
                        "time_of_day" to str(),
                        "weather" to str(),
                        "present" to arr(str()),
                        "description" to str()
                    )
                ),
                "goals" to obj(
                    properties = linkedMapOf(
                        "complete" to arr(str()),
                        "add" to arr(str()),
                        "update" to arr(
                            obj(
                                properties = linkedMapOf(
                                    "from" to str(),
                                    "to" to str()
                                )
                            )
                        ),
                        "current" to str()
                    )
                ),
                "player" to obj(
                    properties = linkedMapOf(
                        "fields" to freeStringMap(),
                        "inventory" to arr(
                            obj(
                                properties = linkedMapOf(
                                    "op" to str(),
                                    "item" to str(),
                                    "qty" to integer()
                                )
                            )
                        )
                    )
                ),
                "npcs" to arr(
                    obj(
                        properties = linkedMapOf(
                            "name" to str(),
                            "emotion" to str(),
                            "favor_delta" to integer(),
                            "memory" to str()
                        ),
                        required = listOf("name")
                    )
                ),
                "new_npcs" to arr(
                    obj(
                        properties = linkedMapOf(
                            "name" to str(),
                            "identity" to str(),
                            "relationship_to_player" to str()
                        ),
                        required = listOf("name")
                    )
                ),
                "suggestions" to arr(str())
            )
        )
    }

    private fun obj(
        properties: Map<String, JsonObject> = emptyMap(),
        required: List<String> = emptyList(),
        additionalProperties: Boolean = false
    ): JsonObject = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            properties.forEach { (k, v) -> add(k, v) }
        })
        if (required.isNotEmpty()) {
            add("required", JsonArray().apply { required.forEach(::add) })
        }
        addProperty("additionalProperties", additionalProperties)
    }

    private fun freeStringMap(): JsonObject = JsonObject().apply {
        addProperty("type", "object")
        addProperty("additionalProperties", true)
    }

    private fun arr(itemSchema: JsonObject): JsonObject = JsonObject().apply {
        addProperty("type", "array")
        add("items", itemSchema)
    }

    private fun str(): JsonObject = JsonObject().apply { addProperty("type", "string") }

    private fun integer(): JsonObject = JsonObject().apply { addProperty("type", "integer") }
}
