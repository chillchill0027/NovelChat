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
        val candidateStages = when (stage) {
            AiCallStage.STATE_KEEPER -> listOf(AiCallStage.STATE_KEEPER, AiCallStage.TASK_ASSIGNMENT)
            AiCallStage.SUMMARIZER -> listOf(
                AiCallStage.SUMMARIZER,
                AiCallStage.STATE_KEEPER,
                AiCallStage.TASK_ASSIGNMENT
            )
            null -> emptyList()
            else -> listOf(stage)
        }
        for (candidate in candidateStages) {
            val routedProfileName = routes.profileName(candidate)
            val matched = profiles.firstOrNull { it.name == routedProfileName }
            if (matched != null) return matched
        }
        return defaultConnection
    }
}
