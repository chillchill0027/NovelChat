package com.novelchat.data.remote.model

import com.google.gson.annotations.SerializedName
import com.novelchat.data.remote.AiCallStage

data class ApiStageRoutes(
    @SerializedName("routes") val routes: Map<String, String> = emptyMap()
) {
    fun profileName(stage: AiCallStage): String =
        routes[stage.storageKey].orEmpty().trim()

    fun assign(stage: AiCallStage, profileName: String): ApiStageRoutes {
        val normalizedName = profileName.trim()
        val updated = routes.toMutableMap()
        if (normalizedName.isBlank()) {
            updated.remove(stage.storageKey)
        } else {
            updated[stage.storageKey] = normalizedName
        }
        return copy(routes = updated)
    }

    fun withoutProfile(profileName: String): ApiStageRoutes {
        val normalizedName = profileName.trim()
        if (normalizedName.isBlank()) return this
        return copy(routes = routes.filterValues { it.trim() != normalizedName })
    }

    fun sanitize(existingProfileNames: Set<String>): ApiStageRoutes {
        val normalizedNames = existingProfileNames.map(String::trim).filter(String::isNotBlank).toSet()
        return copy(routes = routes.mapNotNull { (key, value) ->
            val stage = AiCallStage.fromStorageKey(key) ?: return@mapNotNull null
            val profileName = value.trim().takeIf(normalizedNames::contains)
                ?: return@mapNotNull null
            stage.storageKey to profileName
        }.toMap())
    }
}
