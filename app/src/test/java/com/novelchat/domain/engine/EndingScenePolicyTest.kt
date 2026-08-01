package com.novelchat.domain.engine

import com.novelchat.data.model.EndingScene
import com.novelchat.data.model.StoryStateUpdate
import com.novelchat.data.model.TaskAssignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EndingScenePolicyTest {

    @Test
    fun reconcile_overridesStaleTaskLocationAndPhysicalSceneCharacters() {
        val result = EndingScenePolicy.reconcile(
            assignment = TaskAssignment(
                storyUpdate = StoryStateUpdate(
                    currentLocation = "云桥里老小区",
                    activeCharacters = listOf("电话另一端的林越")
                )
            ),
            endingScene = EndingScene(
                location = "街角彩票店",
                presentCharacters = listOf("林克", "老周", "老周")
            ),
            playerName = "林克",
            sceneDescription = "林克在柜台前等待，老周正在核对票面。"
        )

        assertEquals("街角彩票店", result.storyUpdate?.currentLocation)
        assertEquals(listOf("老周"), result.storyUpdate?.activeCharacters)
        assertEquals("林克在柜台前等待，老周正在核对票面。", result.storyUpdate?.sceneDescription)
    }

    @Test
    fun reconcile_keepsAssignmentWhenNarrativeHasNoSceneAnchor() {
        val assignment = TaskAssignment()

        val result = EndingScenePolicy.reconcile(assignment, EndingScene(), "林克")

        assertNull(result.storyUpdate)
    }
}
