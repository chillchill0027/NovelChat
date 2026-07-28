package com.novelchat.domain.engine

import com.novelchat.data.model.Relationship
import com.novelchat.data.model.RelationshipStatePatch
import com.novelchat.data.model.StoryProgress
import com.novelchat.data.model.StoryProgressPatch

internal object PlayerStatePatchPolicy {

    fun mergeRelationships(
        current: Map<String, Relationship>,
        patches: Map<String, RelationshipStatePatch>
    ): Map<String, Relationship> {
        if (patches.isEmpty()) return current
        return current.toMutableMap().apply {
            patches.forEach { (rawName, patch) ->
                val name = rawName.trim()
                if (name.isBlank() || !hasRelationshipChange(patch)) return@forEach
                val existing = this[name] ?: Relationship()
                this[name] = existing.copy(
                    favor = patch.favor?.coerceIn(-100, 100) ?: existing.favor,
                    trust = patch.trust?.coerceIn(0, 100) ?: existing.trust,
                    stage = patch.stage?.trim()?.takeIf { it.isNotBlank() } ?: existing.stage
                )
            }
        }
    }

    fun mergeStoryProgress(
        current: StoryProgress,
        patch: StoryProgressPatch?
    ): StoryProgress {
        if (patch == null) return current
        return current.copy(
            currentArc = patch.currentArc.nonBlankOr(current.currentArc),
            currentChapter = patch.currentChapter.nonBlankOr(current.currentChapter),
            currentScene = patch.currentScene.nonBlankOr(current.currentScene),
            currentGoal = patch.currentGoal.nonBlankOr(current.currentGoal)
        )
    }

    fun hasRelationshipChange(patch: RelationshipStatePatch): Boolean =
        patch.favor != null || patch.trust != null || !patch.stage.isNullOrBlank()

    fun hasStoryProgressChange(patch: StoryProgressPatch): Boolean =
        !patch.currentArc.isNullOrBlank() ||
            !patch.currentChapter.isNullOrBlank() ||
            !patch.currentScene.isNullOrBlank() ||
            !patch.currentGoal.isNullOrBlank()

    private fun String?.nonBlankOr(fallback: String): String =
        this?.trim()?.takeIf { it.isNotBlank() } ?: fallback
}
