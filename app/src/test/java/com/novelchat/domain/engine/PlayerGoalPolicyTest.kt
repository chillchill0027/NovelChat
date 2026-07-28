package com.novelchat.domain.engine

import com.novelchat.data.model.PlayerState
import com.novelchat.data.model.StoryProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerGoalPolicyTest {

    @Test
    fun alignWithPendingEvents_replacesStaleGoalWithHighestPriorityEvent() {
        val player = PlayerState(
            storyProgress = StoryProgress(currentGoal = "本周交房租")
        )

        val aligned = PlayerGoalPolicy.alignWithPendingEvents(
            player,
            listOf("办理彩票兑奖与奖金领取手续", "本周交房租")
        )

        assertEquals("办理彩票兑奖与奖金领取手续", aligned.storyProgress.currentGoal)
    }

    @Test
    fun alignWithPendingEvents_keepsGoalWhenNoPendingEventExists() {
        val player = PlayerState(
            storyProgress = StoryProgress(currentGoal = "准备面试")
        )

        assertEquals(player, PlayerGoalPolicy.alignWithPendingEvents(player, emptyList()))
    }
}
