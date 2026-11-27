package com.novelchat.ui.viewmodel

import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.PlayerState
import com.novelchat.data.model.StoryProgress
import com.novelchat.data.model.StoryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionSuggestionPolicyTest {

    @Test
    fun suggestions_prioritizeSceneCharacterAndCurrentGoal() {
        val result = ActionSuggestionPolicy.suggestions(
            player = PlayerState(storyProgress = StoryProgress(currentGoal = "提交客户方案")),
            storyState = StoryState(pendingEvents = listOf("明晚面试")),
            npcCards = listOf(CharacterCard(name = "王常"))
        )

        assertEquals("和王常聊聊", result.first())
        assertTrue(result.contains("继续推进当前目标"))
        assertTrue(result.contains("查看待办和时间安排"))
        assertEquals(result.distinct(), result)
    }

    @Test
    fun suggestions_stayUsefulWhenSceneHasNoCharacterOrTask() {
        val result = ActionSuggestionPolicy.suggestions(
            player = PlayerState(),
            storyState = StoryState(),
            npcCards = emptyList()
        )

        assertTrue(result.contains("观察一下周围"))
        assertTrue(result.contains("打听眼下的动向"))
        assertFalse(result.any { it.startsWith("和") })
    }

    @Test
    fun suggestions_honorLimit() {
        val result = ActionSuggestionPolicy.suggestions(
            player = PlayerState(storyProgress = StoryProgress(currentGoal = "推进工作")),
            storyState = StoryState(pendingEvents = listOf("缴费")),
            npcCards = listOf(CharacterCard(name = "顾宁")),
            limit = 2
        )

        assertEquals(2, result.size)
    }
}
