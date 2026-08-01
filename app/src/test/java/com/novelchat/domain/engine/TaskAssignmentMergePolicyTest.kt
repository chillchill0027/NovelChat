package com.novelchat.domain.engine

import com.novelchat.data.model.MentalStatePatch
import com.novelchat.data.model.PhysicalStatePatch
import com.novelchat.data.model.PlayerChanges
import com.novelchat.data.model.PlayerUpdate
import com.novelchat.data.model.StoryStateUpdate
import com.novelchat.data.model.TaskAssignment
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskAssignmentMergePolicyTest {
    @Test
    fun merge_preservesEarlierFieldsAndLetsNewerValuesWin() {
        val earlier = TaskAssignment(
            storyUpdate = StoryStateUpdate(
                currentLocation = "办公室",
                timeOfDay = "清晨",
                sceneDescription = "陆砚正在核对记录。"
            ),
            playerUpdate = PlayerUpdate(
                reason = "主动承认违规",
                changes = PlayerChanges(
                    physicalState = PhysicalStatePatch(stamina = 145),
                    mentalState = MentalStatePatch(stress = "中等"),
                    attributes = mapOf("oath_strain" to "26")
                )
            )
        )
        val newer = TaskAssignment(
            storyUpdate = StoryStateUpdate(currentLocation = "监察员办公室"),
            playerUpdate = PlayerUpdate(
                changes = PlayerChanges(
                    physicalState = PhysicalStatePatch(fatigue = "轻微"),
                    attributes = mapOf("clue_depth" to "38"),
                    risk = 28
                )
            )
        )

        val merged = TaskAssignmentMergePolicy.merge(earlier, newer)

        assertEquals("监察员办公室", merged.storyUpdate?.currentLocation)
        assertEquals("清晨", merged.storyUpdate?.timeOfDay)
        assertEquals("陆砚正在核对记录。", merged.storyUpdate?.sceneDescription)
        assertEquals("主动承认违规", merged.playerUpdate?.reason)
        assertEquals(145, merged.playerUpdate?.changes?.physicalState?.stamina)
        assertEquals("轻微", merged.playerUpdate?.changes?.physicalState?.fatigue)
        assertEquals("中等", merged.playerUpdate?.changes?.mentalState?.stress)
        assertEquals("26", merged.playerUpdate?.changes?.attributes?.get("oath_strain"))
        assertEquals("38", merged.playerUpdate?.changes?.attributes?.get("clue_depth"))
        assertEquals(28, merged.playerUpdate?.changes?.risk)
    }
}
