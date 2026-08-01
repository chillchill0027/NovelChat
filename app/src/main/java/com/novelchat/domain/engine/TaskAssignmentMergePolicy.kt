package com.novelchat.domain.engine

import com.novelchat.data.model.MentalStatePatch
import com.novelchat.data.model.PhysicalStatePatch
import com.novelchat.data.model.PlayerChanges
import com.novelchat.data.model.PlayerUpdate
import com.novelchat.data.model.RelationshipStatePatch
import com.novelchat.data.model.StoryProgressPatch
import com.novelchat.data.model.StoryStateUpdate
import com.novelchat.data.model.TaskAssignment

/** Preserves valid partial state from earlier retries while newer values take precedence. */
internal object TaskAssignmentMergePolicy {
    fun merge(earlier: TaskAssignment?, newer: TaskAssignment): TaskAssignment {
        if (earlier == null) return newer
        return newer.copy(
            storyUpdate = mergeStory(earlier.storyUpdate, newer.storyUpdate),
            playerUpdate = mergePlayer(earlier.playerUpdate, newer.playerUpdate),
            newCharacters = newer.newCharacters.ifEmpty { earlier.newCharacters },
            retiredCharacters = newer.retiredCharacters.ifEmpty { earlier.retiredCharacters },
            characterUpdates = newer.characterUpdates.ifEmpty { earlier.characterUpdates },
            worldbookUpdates = newer.worldbookUpdates.ifEmpty { earlier.worldbookUpdates },
            itemTransfers = newer.itemTransfers.ifEmpty { earlier.itemTransfers },
            events = newer.events.ifEmpty { earlier.events },
            syncStatus = newer.syncStatus.ifBlank { earlier.syncStatus },
            noChangeReason = newer.noChangeReason.ifBlank { earlier.noChangeReason }
        )
    }

    private fun mergeStory(
        earlier: StoryStateUpdate?,
        newer: StoryStateUpdate?
    ): StoryStateUpdate? {
        if (earlier == null) return newer
        if (newer == null) return earlier
        return newer.copy(
            currentLocation = newer.currentLocation ?: earlier.currentLocation,
            timeOfDay = newer.timeOfDay ?: earlier.timeOfDay,
            weather = newer.weather ?: earlier.weather,
            mood = newer.mood ?: earlier.mood,
            sceneDescription = newer.sceneDescription ?: earlier.sceneDescription,
            activeCharacters = newer.activeCharacters ?: earlier.activeCharacters,
            pendingEvents = newer.pendingEvents ?: earlier.pendingEvents
        )
    }

    private fun mergePlayer(earlier: PlayerUpdate?, newer: PlayerUpdate?): PlayerUpdate? {
        if (earlier == null) return newer
        if (newer == null) return earlier
        return newer.copy(
            reason = newer.reason.ifBlank { earlier.reason },
            changes = mergeChanges(earlier.changes, newer.changes)
        )
    }

    private fun mergeChanges(earlier: PlayerChanges, newer: PlayerChanges): PlayerChanges {
        return newer.copy(
            inventory = newer.inventory.ifEmpty { earlier.inventory },
            relationships = mergeRelationships(earlier.relationships, newer.relationships),
            questLog = newer.questLog ?: earlier.questLog,
            storyProgress = mergeProgress(earlier.storyProgress, newer.storyProgress),
            physicalState = mergePhysical(earlier.physicalState, newer.physicalState),
            mentalState = mergeMental(earlier.mentalState, newer.mentalState),
            attributes = earlier.attributes + newer.attributes,
            risk = newer.risk ?: earlier.risk
        )
    }

    private fun mergeRelationships(
        earlier: Map<String, RelationshipStatePatch>,
        newer: Map<String, RelationshipStatePatch>
    ): Map<String, RelationshipStatePatch> {
        if (earlier.isEmpty()) return newer
        if (newer.isEmpty()) return earlier
        return earlier.toMutableMap().apply {
            newer.forEach { (name, patch) ->
                val previous = this[name]
                this[name] = patch.copy(
                    favor = patch.favor ?: previous?.favor,
                    trust = patch.trust ?: previous?.trust,
                    stage = patch.stage ?: previous?.stage
                )
            }
        }
    }

    private fun mergeProgress(
        earlier: StoryProgressPatch?,
        newer: StoryProgressPatch?
    ): StoryProgressPatch? {
        if (earlier == null) return newer
        if (newer == null) return earlier
        return newer.copy(
            currentArc = newer.currentArc ?: earlier.currentArc,
            currentChapter = newer.currentChapter ?: earlier.currentChapter,
            currentScene = newer.currentScene ?: earlier.currentScene,
            currentGoal = newer.currentGoal ?: earlier.currentGoal
        )
    }

    private fun mergePhysical(
        earlier: PhysicalStatePatch?,
        newer: PhysicalStatePatch?
    ): PhysicalStatePatch? {
        if (earlier == null) return newer
        if (newer == null) return earlier
        return newer.copy(
            stamina = newer.stamina ?: earlier.stamina,
            health = newer.health ?: earlier.health,
            injuries = newer.injuries ?: earlier.injuries,
            fatigue = newer.fatigue ?: earlier.fatigue,
            hunger = newer.hunger ?: earlier.hunger,
            pain = newer.pain ?: earlier.pain
        )
    }

    private fun mergeMental(
        earlier: MentalStatePatch?,
        newer: MentalStatePatch?
    ): MentalStatePatch? {
        if (earlier == null) return newer
        if (newer == null) return earlier
        return newer.copy(
            mood = newer.mood ?: earlier.mood,
            stress = newer.stress ?: earlier.stress,
            focus = newer.focus ?: earlier.focus,
            currentThoughts = newer.currentThoughts ?: earlier.currentThoughts
        )
    }
}
