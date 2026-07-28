package com.novelchat.domain.engine

import com.novelchat.data.model.PlayerChanges
import com.novelchat.data.model.StoryStateUpdate
import com.novelchat.data.model.TaskAssignment

internal class EmptyTaskAssignmentException(message: String) : IllegalArgumentException(message)

internal object TaskAssignmentQualityPolicy {

    fun requireMeaningful(assignment: TaskAssignment): TaskAssignment {
        if (!isMeaningful(assignment)) {
            throw EmptyTaskAssignmentException("任务分配没有任何可执行的状态变化")
        }
        return assignment
    }

    fun isMeaningful(assignment: TaskAssignment): Boolean {
        return hasCertifiedNoChange(assignment) ||
            assignment.storyUpdate?.let(::hasStoryChanges) == true ||
            assignment.playerUpdate?.let { update ->
                hasPlayerChanges(update.changes)
            } == true ||
            assignment.newCharacters.any { it.name.isNotBlank() } ||
            assignment.retiredCharacters.any { it.name.isNotBlank() } ||
            assignment.characterUpdates.any { it.name.isNotBlank() } ||
            assignment.worldbookUpdates.any(::hasWorldbookChange) ||
            assignment.itemTransfers.any {
                it.item.isNotBlank() && it.quantity > 0 &&
                    it.from.isNotBlank() && it.to.isNotBlank() && it.from.trim() != it.to.trim()
            } ||
            assignment.events.any { event ->
                event.type.isNotBlank() &&
                    event.target.isNotBlank() &&
                    event.description.isNotBlank()
            }
    }

    private fun hasCertifiedNoChange(assignment: TaskAssignment): Boolean {
        val reason = assignment.noChangeReason.trim()
        return assignment.syncStatus.trim().lowercase() in setOf("no_change", "unchanged") &&
            reason.length >= 8 &&
            reason !in genericNoChangeReasons
    }

    private val genericNoChangeReasons = setOf(
        "没有变化", "无状态变化", "无需更新", "没有需要更新", "no change", "unchanged"
    )

    private fun hasStoryChanges(update: StoryStateUpdate): Boolean {
        return !update.currentLocation.isNullOrBlank() ||
            !update.timeOfDay.isNullOrBlank() ||
            !update.weather.isNullOrBlank() ||
            !update.mood.isNullOrBlank() ||
            update.activeCharacters != null ||
            update.pendingEvents != null
    }

    private fun hasPlayerChanges(changes: PlayerChanges): Boolean {
        return changes.inventory.any {
            it.action.trim().lowercase() in setOf("add", "remove") &&
                it.item.isNotBlank() && it.quantity > 0
        } ||
            changes.relationships.any { (name, patch) ->
                name.isNotBlank() && PlayerStatePatchPolicy.hasRelationshipChange(patch)
            } ||
            changes.questLog != null ||
            changes.storyProgress?.let(PlayerStatePatchPolicy::hasStoryProgressChange) == true ||
            changes.physicalState?.let { patch ->
                patch.stamina != null ||
                    !patch.health.isNullOrBlank() ||
                    patch.injuries != null ||
                    !patch.fatigue.isNullOrBlank() ||
                    !patch.hunger.isNullOrBlank() ||
                    !patch.pain.isNullOrBlank()
            } == true ||
            changes.mentalState?.let { patch ->
                !patch.mood.isNullOrBlank() ||
                    !patch.stress.isNullOrBlank() ||
                    !patch.focus.isNullOrBlank() ||
                    !patch.currentThoughts.isNullOrBlank()
            } == true ||
            changes.risk != null
    }

    private fun hasWorldbookChange(update: com.novelchat.data.model.WorldbookUpdate): Boolean {
        return when (update.action.trim().lowercase()) {
            "create" -> !update.name.isNullOrBlank() && !update.description.isNullOrBlank()
            "update", "delete" -> !update.id.isNullOrBlank() || !update.name.isNullOrBlank()
            else -> false
        }
    }
}
