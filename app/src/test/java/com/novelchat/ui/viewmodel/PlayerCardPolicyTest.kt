package com.novelchat.ui.viewmodel

import com.novelchat.data.model.MentalState
import com.novelchat.data.model.PlayerState
import com.novelchat.data.model.Quest
import com.novelchat.data.model.StoryProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerCardPolicyTest {

    @Test
    fun currentGoal_prefersExplicitStoryGoal() {
        val player = PlayerState(
            storyProgress = StoryProgress(currentGoal = "核对客户原始邮件"),
            mentalState = MentalState(focus = "整理文件")
        )

        assertEquals(
            "核对客户原始邮件",
            PlayerCardPolicy.currentGoal(player, listOf("明天晚上面试"))
        )
    }

    @Test
    fun currentGoal_usesActiveQuestBeforePendingEvent() {
        val player = PlayerState(
            questLog = listOf(Quest(name = "客户方案", status = "进行中", currentStep = "完成风险清单"))
        )

        assertEquals(
            "完成风险清单",
            PlayerCardPolicy.currentGoal(player, listOf("明天晚上面试"))
        )
    }

    @Test
    fun currentGoal_fallsBackToMostUrgentPendingEvent() {
        assertEquals(
            "完成客户响应流程修订方案（周三前）",
            PlayerCardPolicy.currentGoal(
                PlayerState(),
                listOf("完成客户响应流程修订方案（周三前）", "明天晚上面试")
            )
        )
    }
}
