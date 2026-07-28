package com.novelchat.domain.engine

import com.novelchat.data.model.Relationship
import com.novelchat.data.model.RelationshipStatePatch
import com.novelchat.data.model.StoryProgress
import com.novelchat.data.model.StoryProgressPatch
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerStatePatchPolicyTest {

    @Test
    fun mergeRelationships_preservesFieldsMissingFromPatch() {
        val current = mapOf(
            "苏晚" to Relationship(favor = 42, trust = 67, stage = "恋人")
        )

        val updated = PlayerStatePatchPolicy.mergeRelationships(
            current,
            mapOf("苏晚" to RelationshipStatePatch(favor = 48))
        ).getValue("苏晚")

        assertEquals(48, updated.favor)
        assertEquals(67, updated.trust)
        assertEquals("恋人", updated.stage)
    }

    @Test
    fun mergeStoryProgress_preservesArcChapterAndScene() {
        val current = StoryProgress(
            currentArc = "城市转折",
            currentChapter = "意外奖金",
            currentScene = "彩票店",
            currentGoal = "完成兑奖"
        )

        val updated = PlayerStatePatchPolicy.mergeStoryProgress(
            current,
            StoryProgressPatch(currentGoal = "联系银行确认手续")
        )

        assertEquals("城市转折", updated.currentArc)
        assertEquals("意外奖金", updated.currentChapter)
        assertEquals("彩票店", updated.currentScene)
        assertEquals("联系银行确认手续", updated.currentGoal)
    }
}
