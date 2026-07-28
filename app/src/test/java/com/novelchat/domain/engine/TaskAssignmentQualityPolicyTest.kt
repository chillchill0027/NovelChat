package com.novelchat.domain.engine

import com.novelchat.data.model.PlayerChanges
import com.novelchat.data.model.PlayerUpdate
import com.novelchat.data.model.RelationshipStatePatch
import com.novelchat.data.model.StoryStateUpdate
import com.novelchat.data.model.TaskAssignment
import com.novelchat.data.model.EventRecord
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskAssignmentQualityPolicyTest {

    @Test
    fun meaningful_acceptsPersistentEventOnlyAssignment() {
        assertTrue(
            TaskAssignmentQualityPolicy.isMeaningful(
                TaskAssignment(
                    events = listOf(
                        EventRecord(
                            type = "兑奖",
                            target = "彩票店",
                            description = "店员完成中奖彩票核验"
                        )
                    )
                )
            )
        )
    }

    @Test
    fun isMeaningful_rejectsEmptyShells() {
        assertFalse(TaskAssignmentQualityPolicy.isMeaningful(TaskAssignment()))
        assertFalse(
            TaskAssignmentQualityPolicy.isMeaningful(
                TaskAssignment(
                    storyUpdate = StoryStateUpdate(),
                    playerUpdate = PlayerUpdate(changes = PlayerChanges())
                )
            )
        )
        assertFalse(
            TaskAssignmentQualityPolicy.isMeaningful(
                TaskAssignment(playerUpdate = PlayerUpdate(reason = "只是解释，没有变化"))
            )
        )
        assertFalse(
            TaskAssignmentQualityPolicy.isMeaningful(
                TaskAssignment(
                    playerUpdate = PlayerUpdate(
                        changes = PlayerChanges(
                            relationships = mapOf("苏晚" to RelationshipStatePatch())
                        )
                    )
                )
            )
        )
    }

    @Test
    fun isMeaningful_acceptsExplicitSceneClearsAndRiskZero() {
        assertTrue(
            TaskAssignmentQualityPolicy.isMeaningful(
                TaskAssignment(storyUpdate = StoryStateUpdate(activeCharacters = emptyList()))
            )
        )
        assertTrue(
            TaskAssignmentQualityPolicy.isMeaningful(
                TaskAssignment(playerUpdate = PlayerUpdate(changes = PlayerChanges(risk = 0)))
            )
        )
    }

    @Test
    fun isMeaningful_acceptsOnlyExplicitlyCertifiedNoChange() {
        assertTrue(
            TaskAssignmentQualityPolicy.isMeaningful(
                TaskAssignment(
                    syncStatus = "no_change",
                    noChangeReason = "本轮只延续了对话，场景和人物状态均未改变"
                )
            )
        )
        assertFalse(
            TaskAssignmentQualityPolicy.isMeaningful(
                TaskAssignment(syncStatus = "no_change", noChangeReason = "无")
            )
        )
    }

    @Test
    fun isMeaningful_rejectsEventOnlyAssignmentBecauseItDoesNotPersistState() {
        assertFalse(
            TaskAssignmentQualityPolicy.isMeaningful(
                TaskAssignment(
                    events = listOf(
                        com.novelchat.data.model.EventRecord(description = "店员核验了中奖彩票")
                    )
                )
            )
        )
    }
}
