package com.novelchat.domain.engine

import com.novelchat.data.model.EndingScene
import com.novelchat.data.model.StoryStateUpdate
import com.novelchat.data.model.TaskAssignment

internal object EndingScenePolicy {

    fun reconcile(
        assignment: TaskAssignment,
        endingScene: EndingScene,
        playerName: String,
        sceneDescription: String = ""
    ): TaskAssignment {
        val location = endingScene.location.trim()
        val description = sceneDescription.trim()
        val presentCharacters = endingScene.presentCharacters?.map { it.trim() }
            ?.filter { it.isNotBlank() && it != playerName }
            ?.distinct()
        if (location.isBlank() && presentCharacters == null && description.isBlank()) return assignment

        val update = assignment.storyUpdate ?: StoryStateUpdate()
        return assignment.copy(
            storyUpdate = update.copy(
                currentLocation = location.ifBlank { update.currentLocation },
                sceneDescription = description.ifBlank { update.sceneDescription },
                activeCharacters = presentCharacters ?: update.activeCharacters
            )
        )
    }
}
