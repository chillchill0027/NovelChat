package com.novelchat.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class PendingEventPolicyTest {

    @Test
    fun reconcile_removesOnlyEventsWithExplicitCompletionEvidence() {
        val events = listOf(
            "本周交房租",
            "完成客户响应流程修订方案（周三前）",
            "回复高薪面试邀请"
        )
        val context = """
            林克付清全部房租，陈姐开具了收据。
            林克承诺周三前提交客户响应流程修订方案。
            招聘顾问孟琪仍在等待回复。
        """.trimIndent()

        assertEquals(
            listOf("完成客户响应流程修订方案（周三前）", "回复高薪面试邀请"),
            PendingEventPolicy.reconcile(events, context)
        )
    }

    @Test
    fun reconcile_removesSubmittedAndRepliedTasks() {
        val events = listOf("中午前提交第一版修订", "回复高薪面试邀请")
        val context = "林克已经提交了第一版修订。随后他已回复高薪面试邀请。"

        assertEquals(emptyList<String>(), PendingEventPolicy.reconcile(events, context))
    }

    @Test
    fun reconcile_keepsOrderAndDeduplicatesUnresolvedEvents() {
        val events = listOf("准备客户会议", "回复招聘顾问", "准备客户会议")

        assertEquals(
            listOf("准备客户会议", "回复招聘顾问"),
            PendingEventPolicy.reconcile(events, "林克查看了日程，但尚未完成任何事项。")
        )
    }

    @Test
    fun reconcile_doesNotTreatReplyAboutFutureInterviewAsInterviewCompletion() {
        val events = listOf("明天晚上面试（等待薪资范围回复）")
        val context = "孟琪回复了消息：明天晚上面试照常，薪资范围稍后发来。"

        assertEquals(events, PendingEventPolicy.reconcile(events, context))
    }

    @Test
    fun reconcile_doesNotTreatDraftAsWholeTaskCompletion() {
        val events = listOf("完成客户响应流程修订方案（周三前）")
        val context = "林克完成了初稿框架，但仍需在周三前定稿并提交。"

        assertEquals(events, PendingEventPolicy.reconcile(events, context))
    }

    @Test
    fun reconcile_usesActionSpecificCompletionMarkers() {
        val meeting = "参加客户会议"
        val reply = "回复客户会议邀请"
        val context = "林克已回复客户会议邀请。"

        assertEquals(
            listOf(meeting),
            PendingEventPolicy.reconcile(listOf(meeting, reply), context)
        )
    }

    @Test
    fun prioritizeImmediateConsequences_addsUnfinishedLotteryClaimAheadOfOldTasks() {
        val narrative = "林克把彩票递给老周核验。屏幕显示他中了一等奖五百万元，老周请他先出示身份证。"

        assertEquals(
            listOf("办理彩票兑奖与奖金领取手续", "本周交房租", "周四下午面试"),
            PendingEventPolicy.prioritizeImmediateConsequences(
                listOf("本周交房租", "周四下午面试"),
                narrative
            )
        )
    }

    @Test
    fun prioritizeImmediateConsequences_movesExistingClaimAndSkipsCompletedClaim() {
        val unresolved = "林克的彩票中了一等奖，奖金仍需到省中心办理领取手续。"
        assertEquals(
            listOf("前往省中心兑奖", "本周交房租"),
            PendingEventPolicy.prioritizeImmediateConsequences(
                listOf("本周交房租", "前往省中心兑奖"),
                unresolved
            )
        )

        val completed = "林克的彩票中了一等奖，他办完兑奖手续，奖金已经到账。"
        assertEquals(
            listOf("本周交房租"),
            PendingEventPolicy.prioritizeImmediateConsequences(listOf("本周交房租"), completed)
        )
    }

    @Test
    fun prioritizeImmediateConsequences_removesSmallModelTemplateFragments() {
        val narrative = "林克说明自己中了彩票，周明把他带到A07柜台，从托盘里取出兑奖登记册递给他。"
        val generated = listOf(
            "办理彩票兑奖与奖金领取手续",
            "已获得专人引导至A07柜台",
            "并开始登记基本信息。",
            "尚未执行。",
            "尚未安排时间或地点。",
            "尚未到达该日期",
            "时间。"
        )

        assertEquals(
            listOf("在A07柜台填写兑奖登记信息"),
            PendingEventPolicy.prioritizeImmediateConsequences(generated, narrative)
        )
    }

    @Test
    fun prioritizeImmediateConsequences_keepsSpecificUnfinishedTasks() {
        val events = listOf(
            "并等待省中心复核。",
            "尚未回复客户",
            "已约定周五面试，需按时赴约"
        )

        assertEquals(
            listOf("等待省中心复核", "尚未回复客户", "已约定周五面试，需按时赴约"),
            PendingEventPolicy.prioritizeImmediateConsequences(events, "林克查看了接下来的安排。")
        )
    }
}
