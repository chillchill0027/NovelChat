package com.novelchat.data.storage

import com.novelchat.data.remote.AiCallStage
import com.novelchat.data.remote.model.ApiProfile
import com.novelchat.data.remote.model.ApiStageRoutes

internal object ApiConnectionRoutingPolicy {
    fun resolve(
        defaultConnection: ApiProfile,
        profiles: List<ApiProfile>,
        routes: ApiStageRoutes,
        stage: AiCallStage?
    ): ApiProfile {
        val routedProfileName = stage?.let(routes::profileName).orEmpty()
        return profiles.firstOrNull { it.name == routedProfileName } ?: defaultConnection
    }
}
