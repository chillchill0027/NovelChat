package com.novelchat.domain.engine

import com.novelchat.data.model.ActionCoverage
import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.EndingScene
import com.novelchat.data.model.ForwardHook
import com.novelchat.data.model.IntroducedCharacter
import com.novelchat.data.model.NarrativeEntry
import com.novelchat.data.model.NarrativeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NarrativeQualityPolicyTest {

    @Test
    fun isStyleOnlyIssue_distinguishesCopyEditingFromStructuralRepairs() {
        assertTrue(
            NarrativeQualityPolicy.isStyleOnlyIssue(
                "正文有 6 处说明书式旁白，必须只保留人物可观察到的变化；正文直接引用上一轮等应用元信息"
            )
        )
        assertTrue(
            !NarrativeQualityPolicy.isStyleOnlyIssue(
                "动作覆盖证据并非正文中的原句；正文有 3 处抽象包装"
            )
        )
        assertTrue(!NarrativeQualityPolicy.isStyleOnlyIssue(null))
        assertTrue(
            NarrativeQualityPolicy.containsContinuationRepetition(
                "正文重复上一轮已经说过的对白，续写必须产生新事实"
            )
        )
        assertTrue(
            !NarrativeQualityPolicy.containsContinuationRepetition(
                "正文有 4 处界面流程解说"
            )
        )
    }

    @Test
    fun targetLengthIssue_usesSoftOneThousandToFifteenHundredCharacterRange() {
        val tooShort = NarrativeQualityPolicy.targetLengthIssue("字".repeat(999))
        val tooLong = NarrativeQualityPolicy.targetLengthIssue("字".repeat(1501))

        assertTrue(tooShort?.contains("1000-1500") == true)
        assertTrue(tooLong?.contains("1000-1500") == true)
        assertNull(NarrativeQualityPolicy.targetLengthIssue("字".repeat(1000)))
        assertNull(NarrativeQualityPolicy.targetLengthIssue("字".repeat(1500)))
        assertTrue(NarrativeQualityPolicy.isStyleOnlyIssue(tooShort))
        assertFalse(NarrativeQualityPolicy.containsBlockingIssue(tooShort))
        assertTrue(NarrativeQualityPolicy.containsTargetLengthIssue(tooShort))
        assertTrue(NarrativeQualityPolicy.isNonBlockingLengthIssue(tooShort))
        assertFalse(
            NarrativeQualityPolicy.isNonBlockingLengthIssue(
                "$tooShort；动作覆盖证据没有按照用户行动的原顺序出现"
            )
        )
    }

    @Test
    fun targetLengthImprovement_keepsCloserIntermediateExpansion() {
        val current = "字".repeat(781)
        val repaired = "字".repeat(920)

        assertTrue(
            NarrativeQualityPolicy.isTargetLengthImprovement(
                currentNarrative = current,
                repairedNarrative = repaired,
                currentIssue = NarrativeQualityPolicy.targetLengthIssue(current),
                repairedIssue = NarrativeQualityPolicy.targetLengthIssue(repaired)
            )
        )
        assertFalse(
            NarrativeQualityPolicy.isTargetLengthImprovement(
                currentNarrative = repaired,
                repairedNarrative = current,
                currentIssue = NarrativeQualityPolicy.targetLengthIssue(repaired),
                repairedIssue = NarrativeQualityPolicy.targetLengthIssue(current)
            )
        )
    }

    @Test
    fun issueSeverityScore_prioritizesMissingCharacterGroundingOverStyle() {
        val groundingScore = NarrativeQualityPolicy.issueSeverityScore(
            "首次登场人物张经理没有以真实姓名出现在正文中"
        )
        val styleScore = NarrativeQualityPolicy.issueSeverityScore(
            "正文反复解释目标、使命感或步伐节奏，必须改写为实际行动与外部反馈"
        )

        assertTrue(groundingScore > styleScore)
        assertTrue(
            NarrativeQualityPolicy.isRepairImprovement(
                currentIssue = "首次登场人物张经理没有以真实姓名出现在正文中",
                repairedIssue = "正文反复解释目标、使命感或步伐节奏，必须改写为实际行动与外部反馈"
            )
        )
        assertTrue(
            !NarrativeQualityPolicy.isRepairImprovement(
                currentIssue = "正文包含重复的报告腔或解释腔表达",
                repairedIssue = "正文反复解释目标、使命感或步伐节奏，必须改写为实际行动与外部反馈"
            )
        )
    }

    @Test
    fun isRepairImprovement_prefersFewerOccurrencesOfSameStyleProblem() {
        val firstIssue = "正文有 8 处替人物解释效率、目标或行为姿态，必须改写为动作、对白与流程反馈"
        val repairedIssue = "正文有 3 处替人物解释效率、目标或行为姿态，必须改写为动作、对白与流程反馈"

        assertTrue(
            NarrativeQualityPolicy.issueSeverityScore(firstIssue) >
                NarrativeQualityPolicy.issueSeverityScore(repairedIssue)
        )
        assertTrue(NarrativeQualityPolicy.isRepairImprovement(firstIssue, repairedIssue))
    }

    @Test
    fun coverageIssue_rejectsRepeatedMechanicalSentenceTransitions() {
        val narrative = """
            林克把房门轻轻带上，确认林悦已经躺好。她隔着门叮嘱他早点回来。

            然后，他走进楼下咖啡厅，接过一杯热咖啡。店员把杯套推到他手边。

            接着，他来到彩票店，把彩票递过柜台。周晴在终端上重新核验了一遍号码。

            最后，周晴的手停在确认键上，压低声音叫来了经理。经理锁上侧门，请林克先坐下。
        """.trimIndent()
        val result = NarrativeResult(
            narrative = narrative,
            actionCoverage = listOf(
                ActionCoverage("安置女朋友", "确认林悦已经躺好"),
                ActionCoverage("买咖啡", "接过一杯热咖啡"),
                ActionCoverage("彩票店兑奖", "把彩票递过柜台")
            ),
            forwardHook = ForwardHook(
                cause = "终端显示巨额奖金",
                evidence = "经理锁上侧门"
            ),
            endingScene = EndingScene(
                location = "彩票店",
                presentCharacters = listOf("周晴")
            ),
            introducedCharacters = listOf(
                IntroducedCharacter(
                    name = "林悦",
                    occupation = "自由插画师",
                    relationshipToPlayer = "女朋友",
                    firstAppearance = "林悦在家休息"
                ),
                IntroducedCharacter(
                    name = "周晴",
                    occupation = "彩票店店员",
                    relationshipToPlayer = "初次见面的店员",
                    firstAppearance = "周晴核验彩票"
                )
            )
        )

        val issue = NarrativeQualityPolicy.coverageIssue(
            result = result,
            userMessage = "安置好女朋友之后，去楼下咖啡厅买一杯咖啡，去彩票店兑奖。"
        )

        assertTrue(issue?.contains("像动作清单") == true)
    }

    @Test
    fun estimateActionCount_countsChineseAndEnglishSequences() {
        assertEquals(
            3,
            NarrativeQualityPolicy.estimateActionCount("安置好女朋友之后，去咖啡厅买咖啡，去彩票店兑奖。")
        )
        assertEquals(
            3,
            NarrativeQualityPolicy.estimateActionCount("I pay the rent, then call Lin Yue. I ask why he cannot attend.")
        )
        assertEquals(
            listOf(
                "After taking care of my girlfriend",
                "I go downstairs to buy coffee",
                "redeem my lottery ticket"
            ),
            NarrativeQualityPolicy.atomicActionHints(
                "After taking care of my girlfriend, I go downstairs to buy coffee, then redeem my lottery ticket."
            )
        )
        assertEquals(
            listOf("我先去咖啡店", "去彩票店兑奖", "回家核对手续"),
            NarrativeQualityPolicy.atomicActionHints("我先去咖啡店再去彩票店兑奖最后回家核对手续")
        )
        assertEquals(
            listOf("推动当前目标取得实质进展：办理彩票兑奖与奖金领取手续"),
            NarrativeQualityPolicy.atomicActionHints(
                "继续推进当前目标",
                "办理彩票兑奖与奖金领取手续"
            )
        )
    }

    @Test
    fun estimateActionCount_ignoresPunctuationInsideDialogue() {
        assertEquals(
            2,
            NarrativeQualityPolicy.estimateActionCount("我对张伟说：“你好，资料我看过了，我会处理。”然后坐下等他回答。")
        )
    }

    @Test
    fun coverageIssue_rejectsSummaryOnlyCoverage() {
        val result = NarrativeResult(
            narrative = "林克付清了房租。".repeat(15),
            summary = "林克付租后给林越打电话，并追问他缺席的原因。",
            actionCoverage = listOf(ActionCoverage("支付房租", "林克付清了房租"))
        )

        val issue = NarrativeQualityPolicy.coverageIssue(
            result,
            "I pay the rent, then call Lin Yue. I ask why he cannot attend."
        )

        assertTrue(issue?.contains("3 个连续动作") == true)
    }

    @Test
    fun coverageIssue_acceptsDistinctEvidencePresentInNarrative() {
        val narrative = buildString {
            append("林克把整月房租交到陈姐手里，接过盖章收据。回到楼道，他拨通林越的电话，同意代为出席紧急会议。\n\n")
            append("林克追问他为何缺席，听见对面停顿后才说要去接孩子。\n\n")
            append("地铁报站声从听筒里漏出来，林越的解释顿时显得可疑。")
            append("林克没有拆穿，只让他立刻发来客户资料，并在原地等候。手机震动时，他又核对了会议时间和客户名单。")
        }
        val result = NarrativeResult(
            narrative = narrative,
            summary = "林克缴租后答应替林越开会，并察觉其解释可疑。",
            actionCoverage = listOf(
                ActionCoverage("支付房租", "林克把整月房租交到陈姐手里"),
                ActionCoverage("联系并同意顶会", "他拨通林越的电话"),
                ActionCoverage("追问原因", "林克追问他为何缺席")
            ),
            forwardHook = ForwardHook(
                cause = "电话背景声暴露了新的疑点",
                evidence = "地铁报站声从听筒里漏出来"
            ),
            endingScene = EndingScene(location = "家中楼道", presentCharacters = emptyList())
        )

        assertNull(
            NarrativeQualityPolicy.coverageIssue(
                result,
                "I pay the rent, then call Lin Yue. I ask why he cannot attend."
            )
        )
    }

    @Test
    fun coverageIssue_acceptsQuotedEvidenceJoinedWithEllipsis() {
        val narrative = buildString {
            append("林克翻出林越的号码拨了过去，开口要求他马上发送纪要，对面短暂沉默后答应下来。\n\n")
            append("他侧耳听见春熙路站的报站声，并追问林越到底在哪里，林越回答时明显放慢了语速。\n\n")
            append("收到文件后，林克边走边往下拉文件，在公交车上读完客户批注，并圈出了需要确认的日期。他把三处冲突逐条记进备忘录，准备到站后马上核实。")
        }
        val result = NarrativeResult(
            narrative = narrative,
            actionCoverage = listOf(
                ActionCoverage("打电话", "“翻出林越的号码拨了过去……马上发送纪要”"),
                ActionCoverage("追问位置", "“听见春熙路站的报站声”"),
                ActionCoverage("阅读文件", "“林克边走边往下拉文件”")
            ),
            forwardHook = ForwardHook(
                cause = "客户批注暴露新的时间冲突",
                evidence = "并圈出了需要确认的日期"
            ),
            endingScene = EndingScene(location = "公交车内", presentCharacters = emptyList())
        )

        assertNull(
            NarrativeQualityPolicy.coverageIssue(
                result,
                "I call Lin Yue. I ask where he is. I read the notes on the way."
            )
        )
    }

    @Test
    fun coverageIssue_rejectsNarrativeWithoutForwardHook() {
        val narrative = "林克把中奖彩票递进窗口，店员先核验票面和防伪纹路，又把号码逐项录入终端。\n\n屏幕亮出一亿元奖金后，店员愣了几秒才请主管过来复核，柜台周围的声音也随之低了下去。主管当场要求封存票据，并联系省中心进行二次确认。"
        val result = NarrativeResult(
            narrative = narrative,
            actionCoverage = listOf(ActionCoverage("去彩票店兑奖", "林克把中奖彩票递进窗口"))
        )

        val issue = NarrativeQualityPolicy.coverageIssue(result, "去彩票店兑奖")

        assertTrue(issue?.contains("forward_hook") == true)
    }

    @Test
    fun coverageIssue_rejectsMajorLotteryOutcomeWithoutVisibleAftermath() {
        val narrative = buildString {
            append("林克走进彩票店，把印着一亿元奖金的彩票和身份证递给店员李明远。李明远接过票据，低头看向终端。\n\n")
            append("林克将中奖凭证推到玻璃窗口下方，心中最后一丝紧张也随之消散，安静等着对方处理。")
        }
        val result = NarrativeResult(
            narrative = narrative,
            actionCoverage = listOf(
                ActionCoverage("领取一亿元奖金", "林克将中奖凭证推到玻璃窗口下方")
            ),
            forwardHook = ForwardHook(
                cause = "林克已经递交中奖凭证",
                evidence = "心中最后一丝紧张也随之消散"
            ),
            endingScene = EndingScene("彩票店兑奖窗口", listOf("李明远")),
            introducedCharacters = listOf(
                IntroducedCharacter("李明远", "彩票店店员", "初识", "李明远接过中奖票据")
            )
        )

        val issue = NarrativeQualityPolicy.coverageIssue(
            result,
            "I visit the lottery shop to claim my 100 million yuan jackpot."
        )

        assertTrue(issue?.contains("高影响结果") == true)
    }

    @Test
    fun coverageIssue_acceptsVisibleReactionAndVerificationAfterMajorLotteryOutcome() {
        val narrative = buildString {
            append("林克走进彩票店，把印着一亿元奖金的彩票和身份证递给店员李明远。李明远接过票据，低头看向终端。\n\n")
            append("林克将中奖凭证推到玻璃窗口下方。屏幕亮出金额后，李明远的手停在键盘上，重新核对了三遍号码，随后锁住终端并叫来主管复核。")
        }
        val result = NarrativeResult(
            narrative = narrative,
            actionCoverage = listOf(
                ActionCoverage("领取一亿元奖金", "林克将中奖凭证推到玻璃窗口下方")
            ),
            forwardHook = ForwardHook(
                cause = "终端显示一亿元奖金",
                evidence = "李明远的手停在键盘上"
            ),
            endingScene = EndingScene("彩票店兑奖窗口", listOf("李明远")),
            introducedCharacters = listOf(
                IntroducedCharacter("李明远", "彩票店店员", "初识", "李明远接过中奖票据")
            )
        )

        assertNull(
            NarrativeQualityPolicy.coverageIssue(
                result,
                "I visit the lottery shop to claim my 100 million yuan jackpot."
            )
        )
    }

    @Test
    fun coverageIssue_rejectsChangedAmountEvenWithObserverReactionAndVerification() {
        val narrative = buildString {
            append("林克走进彩票店，把中奖彩票和身份证递给店员李明远。\n\n")
            append("林克将中奖凭证推到玻璃窗口下方。屏幕亮出一千万元后，李明远愣在柜台前，重新核对了三遍号码，随后叫来主管复核。")
        }
        val result = NarrativeResult(
            narrative = narrative,
            actionCoverage = listOf(
                ActionCoverage("领取一亿元奖金", "林克将中奖凭证推到玻璃窗口下方")
            ),
            forwardHook = ForwardHook(
                cause = "终端显示中奖金额",
                evidence = "李明远愣在柜台前"
            ),
            endingScene = EndingScene("彩票店兑奖窗口", listOf("李明远")),
            introducedCharacters = listOf(
                IntroducedCharacter("李明远", "彩票店店员", "初识", "李明远接过中奖票据")
            )
        )

        val issue = NarrativeQualityPolicy.coverageIssue(
            result,
            "I visit the lottery shop to claim my 100 million yuan jackpot."
        )

        assertTrue(issue?.contains("金额没有原样保留") == true)
    }

    @Test
    fun coverageIssue_rejectsPlayerOnlyReactionAfterMajorLotteryOutcome() {
        val narrative = buildString {
            append("林克走进彩票店，把印着一亿元奖金的彩票和身份证递给店员李明远。\n\n")
            append("林克将中奖凭证推到玻璃窗口下方。金额亮起时林克愣了一下，李明远随即重新核对号码并叫来主管复核。")
        }
        val result = NarrativeResult(
            narrative = narrative,
            actionCoverage = listOf(
                ActionCoverage("领取一亿元奖金", "林克将中奖凭证推到玻璃窗口下方")
            ),
            forwardHook = ForwardHook(
                cause = "终端显示一亿元奖金",
                evidence = "金额亮起时林克愣了一下"
            ),
            endingScene = EndingScene("彩票店兑奖窗口", listOf("李明远")),
            introducedCharacters = listOf(
                IntroducedCharacter("李明远", "彩票店店员", "初识", "李明远接过中奖票据")
            )
        )

        val issue = NarrativeQualityPolicy.coverageIssue(
            result,
            "I visit the lottery shop to claim my 100 million yuan jackpot.",
            playerName = "林克"
        )

        assertTrue(issue?.contains("具名兑奖人员") == true)
        assertFalse(issue?.contains("核验或兑奖流程") == true)
    }

    @Test
    fun coverageIssue_rejectsActionEvidenceThatAppearsOutOfOrder() {
        val narrative = buildString {
            append("林克先走进楼下咖啡店，等咖啡师磨豆、萃取，再接过封好杯盖的热咖啡并扫码付款。")
            append("他确认小票和杯身标签无误，才沿街走向下一处目的地。\n\n")
            append("抵达彩票店后，林克把彩票和身份证件递进窗口，等工作人员逐项核验票面、防伪纹路与终端号码。")
            append("屏幕亮起后，值班经理随即要求封存票据并启动二次复核流程。")
        }
        val result = NarrativeResult(
            narrative = narrative,
            actionCoverage = listOf(
                ActionCoverage("彩票店兑奖", "抵达彩票店后，林克把彩票和身份证件递进窗口"),
                ActionCoverage("购买咖啡", "林克先走进楼下咖啡店")
            ),
            forwardHook = ForwardHook("中奖结果触发复核", "值班经理随即要求封存票据"),
            endingScene = EndingScene("彩票店内", emptyList())
        )

        val issue = NarrativeQualityPolicy.coverageIssue(result, "去咖啡店买咖啡，然后去彩票店兑奖")

        assertTrue(issue?.contains("原顺序") == true)
    }

    @Test
    fun coverageIssue_rejectsHookPlacedBeforeLastUserAction() {
        val narrative = "店员忽然叫来主管，低声提醒这张彩票的奖金异常高，柜台周围很快安静下来，玻璃后的工作人员也停下了交谈。\n\n林克随后把中奖彩票递进窗口，耐心等店员核验票面、防伪纹路和终端号码，又按要求出示身份证件并签署核验单。"
        val result = NarrativeResult(
            narrative = narrative,
            actionCoverage = listOf(ActionCoverage("递交彩票兑奖", "林克随后把中奖彩票递进窗口")),
            forwardHook = ForwardHook(
                cause = "奖金异常",
                evidence = "店员忽然叫来主管"
            )
        )

        val issue = NarrativeQualityPolicy.coverageIssue(result, "递交彩票兑奖")

        assertTrue(issue?.contains("最后一个用户动作之后") == true)
    }

    @Test
    fun coverageIssue_rejectsGenericInteractiveCharactersWithoutRosterAnchors() {
        val narrative = buildString {
            append("林克替女朋友掖好被角，又把温水和药放在床头，确认她能安稳休息。\n\n")
            append("他下楼走进咖啡店，看着店员封好一杯热咖啡，再扫码付了钱。\n\n")
            append("林克随后来到彩票店，把票递给老板核验，屏幕很快亮出中奖结果。\n\n")
            append("老板愣了几秒，立刻锁住终端并请林克出示身份证件。")
        }
        val result = NarrativeResult(
            narrative = narrative,
            actionCoverage = listOf(
                ActionCoverage("照顾女朋友", "林克替女朋友掖好被角"),
                ActionCoverage("购买咖啡", "看着店员封好一杯热咖啡"),
                ActionCoverage("彩票兑奖", "把票递给老板核验")
            ),
            forwardHook = ForwardHook("中奖触发兑奖流程", "立刻锁住终端并请林克出示身份证件"),
            endingScene = EndingScene("彩票店内", listOf("彩票店老板"))
        )

        val issue = NarrativeQualityPolicy.coverageIssue(
            result,
            "After taking care of my girlfriend, I buy coffee, then redeem my lottery ticket."
        )

        assertTrue(issue?.contains("真实姓名") == true || issue?.contains("introduced_characters") == true)
    }

    @Test
    fun coverageIssue_acceptsNamedIntroducedCharactersAcrossMultiActionNarrative() {
        val narrative = buildString {
            append("林克替女友苏晚掖好被角，把温水和药放到她伸手可及的位置，等她安稳睡下才出门。\n\n")
            append("楼下咖啡店的店员阿远替他封好一杯热咖啡，林克扫码付款后接过纸杯。\n\n")
            append("他走进巷口的彩票店，把票递给店主老周。老周逐项核验票面、防伪纹路和终端号码。\n\n")
            append("屏幕亮出奖金后，老周的手停在键盘上，随即锁住终端并请林克出示身份证件。")
        }
        val result = NarrativeResult(
            narrative = narrative,
            actionCoverage = listOf(
                ActionCoverage("照顾女朋友", "林克替女友苏晚掖好被角"),
                ActionCoverage("购买咖啡", "林克扫码付款后接过纸杯"),
                ActionCoverage("彩票兑奖", "把票递给店主老周")
            ),
            forwardHook = ForwardHook("中奖触发严格核验", "随即锁住终端并请林克出示身份证件"),
            endingScene = EndingScene("彩票店内", listOf("老周")),
            introducedCharacters = listOf(
                IntroducedCharacter("苏晚", "插画师", "女朋友", "苏晚在家休息"),
                IntroducedCharacter("阿远", "咖啡店店员", "初识", "阿远为林克制作咖啡"),
                IntroducedCharacter("老周", "彩票店老板", "初识", "老周为林克核验彩票")
            )
        )

        assertNull(
            NarrativeQualityPolicy.coverageIssue(
                result,
                "After taking care of my girlfriend, I buy coffee, then redeem my lottery ticket."
            )
        )
    }

    @Test
    fun coverageIssue_rejectsUnnamedStrangerWhoDirectlyInteracts() {
        val narrative = buildString {
            append("林克锁好房门走出小区，沿着人行道往公交站赶。一个中年男人从拐角冲出来，差点撞上林克，侧身后喘着气说了句抱歉。\n\n")
            append("男人落在地上的证件夹没有合严，一张带有近岸商务区标志的门禁卡滑到林克脚边，他停下来弯腰捡起。")
        }
        val result = NarrativeResult(
            narrative = narrative,
            actionCoverage = listOf(ActionCoverage("走出小区", "林克锁好房门走出小区")),
            forwardHook = ForwardHook("碰撞留下新线索", "一张带有近岸商务区标志的门禁卡滑到林克脚边"),
            endingScene = EndingScene("小区外人行道", emptyList())
        )

        val issue = NarrativeQualityPolicy.coverageIssue(result, "走出小区")

        assertTrue(issue?.contains("陌生人物") == true)
    }

    @Test
    fun coverageIssue_rejectsContinuationThatRephrasesPriorTurnAndExplainsMotivation() {
        val previous = """
            林克挂断了苏晚的电话，站在彩票店门口的人行道上。那种刚刚涌上心头的狂喜并未消退，反而被一种强烈的使命感迅速取代。五百万的奖金不再是一个遥远的数字，而是需要通过正式流程兑现的、具体的现实财富。他深吸一口气，将目光投向远方街道尽头那块醒目的牌子——省福彩中心。身份证、原件、银行卡，这三样东西必须齐备。

            他不再原地停留观望，而是迈开步伐朝着主干道走去。这个动作带着一种坚定的目的性，仿佛将刚才电话中的喜悦迅速转化为具体的行动力。林克没有急着奔跑，而是保持着一个稳定、有节奏的步频，每一步都踩得踏实有力，目标只有一个：尽快抵达福彩中心，完成兑奖手续。

            当他跨过路缘石，沿着人行道向前走去时，手机再次发出了轻微的震动声。林克本能地抬起手去看——屏幕亮着，是苏晚发来的新消息：“小克，我快到高铁站出口了，马上叫车！”这条简洁而有力的信息像一记精准的鼓点，打在了他正在执行任务的心弦上，让他的兴奋感又提升了一个台阶。

            看到苏晚确认自己已经接近目的地，林克的步频明显加快了半个节奏，带着一种迫切的期待感。他加速穿梭在早晨涌动的人流中，目光越过路边摊贩堆积的花卉、迎面走来的上班族，死死地盯着那块写着“省福彩中心”的指示牌。在他身边，一位正在摆放早餐的老奶奶看到他这股子快步走的劲头，忍不住侧过身来问道：“小伙子，这么急啊？去赶什么车？”林克停顿了半秒，抬头朝她露出一个略带兴奋的笑容，只是简单地回答道：“去办个事，老太太。”
        """.trimIndent()
        val current = """
            林克将目光投向远方街道尽头那块醒目的牌子——省福彩中心。他心中已经完成了从“兴奋”到“使命驱动”的转变，五百万不再是一个美好的想象，而是必须通过正式手续落地的现实财富。身份证、原件、银行卡。这三个要素如同三根支柱，支撑着他坚定的行进方向。

            他没有丝毫停滞或犹豫，迈开步伐沿着人行道坚定地朝着主干道走去。他的步频稳定而有力，每一步都踏得沉稳，仿佛正在丈量着通往巨额奖金的距离。当他跨过路缘石时，手机再次发出了轻微的震动声。林克本能地抬起手去看——屏幕亮着，是苏晚发来的最新消息：“小克，我快到高铁站出口了，马上叫车！”这条简洁而有力的信息像一记精准的催促鼓点，瞬间将他体内积聚的兴奋感提升到了一个更急迫的高度。

            看到苏晚已经进入临近阶段，林克的步伐明显加快了半个节奏。他开始在早晨涌动的人流中穿梭、加速，目光死死地盯着那块指示牌，仿佛要将它刻进视网膜里。他的身体语言完全被目标所支配，原本只是“走路”，现在变成了带着明确目的性的“冲刺”。

            当他靠近一家早餐摊时，一位正忙着整理货架的老奶奶抬起头，看到了这股子急切的劲儿。她停下手里的动作，侧过身来，声音里带着一丝关切问道：“小伙子，这么急啊？去赶什么车？”

            林克在加速的过程中不得不微微减速，他抬头对王奶奶露出了一个略带兴奋而又充满力量的笑容，语气简洁地回应道：“去办个事，老太太。”他没有多余的赘述，只是迅速点头致意，随后便重新将身体重心向前推去。有了这份被关注的动力和苏晚临近的信号，他加速穿过摊位之间的缝隙，离那块写着“省福彩中心”的指示牌越来越近。
        """.trimIndent()
        val result = NarrativeResult(
            narrative = current,
            actionCoverage = listOf(
                ActionCoverage("推进兑奖目标", "迈开步伐沿着人行道坚定地朝着主干道走去")
            ),
            forwardHook = ForwardHook(
                cause = "早餐摊主注意到林克",
                evidence = "王奶奶又停下手里的动作问他去赶什么车"
            ),
            endingScene = EndingScene("主干道人行道", listOf("王奶奶"))
        )

        val issue = NarrativeQualityPolicy.coverageIssue(
            result = result,
            userMessage = "继续推进当前目标",
            knownCharacters = listOf(CharacterCard(name = "王奶奶", occupation = "早餐摊店主")),
            recentNarratives = listOf(NarrativeEntry(turn = 4, narrative = previous)),
            currentGoal = "办理彩票兑奖与奖金领取手续"
        )

        assertTrue(issue?.contains("复述上一轮") == true)
        assertTrue(issue?.contains("使命感或步伐节奏") == true)
    }

    @Test
    fun coverageIssue_acceptsContinuationThatMovesIntoNextConcreteProcessStep() {
        val previous = """
            林克收好彩票和证件，离开云桥里的彩票店。街口的早班车刚刚驶过，他在站牌下确认了省福彩中心的地址。

            手机屏幕熄灭后，他沿主干道向东走去。早餐摊的蒸汽从身后散开，福彩中心的指示牌已经出现在前方路口。
        """.trimIndent()
        val current = """
            十分钟后，林克推开省福彩中心的玻璃门。取号机吐出一张纸票，他按屏幕提示选择了大奖兑奖，号码随即出现在等候区上方。

            轮到他时，工作人员顾宁先请他出示彩票和身份证，再戴上手套核验票面与防伪纹路。终端读出一等奖记录后，她没有立即归还彩票，而是按下内线，请复核主管到窗口。

            顾宁把一份兑奖登记表推到林克面前，指明银行卡信息和税务确认栏。侧门随后打开，复核主管带着封存袋走了进来，手续正式进入双人复核。
        """.trimIndent()
        val result = NarrativeResult(
            narrative = current,
            actionCoverage = listOf(
                ActionCoverage("推进兑奖手续", "林克推开省福彩中心的玻璃门")
            ),
            forwardHook = ForwardHook(
                cause = "终端确认一等奖后需要双人复核",
                evidence = "复核主管带着封存袋走了进来"
            ),
            endingScene = EndingScene("省福彩中心兑奖窗口", listOf("顾宁"))
        )

        assertNull(
            NarrativeQualityPolicy.coverageIssue(
                result = result,
                userMessage = "继续推进当前目标",
                knownCharacters = listOf(CharacterCard(name = "顾宁", occupation = "福彩中心工作人员")),
                recentNarratives = listOf(NarrativeEntry(turn = 5, narrative = previous)),
                currentGoal = "办理彩票兑奖与奖金领取手续"
            )
        )
    }

    @Test
    fun coverageIssue_rejectsUnauthorizedMajorDecisionDuringGenericContinuation() {
        val narrative = """
            李静核对完登记信息，把兑奖方式确认单推到林克面前。“奖金可以转入银行卡，也可以先申请其他兑现方式。”

            周明站在柜台旁，没有替他催促。最终，林克看过确认单，清晰地开口回答：“直接打款到我的储蓄卡上就好，谢谢。”

            李静随即在银行转账一栏画了勾，屏幕弹出等待提交的确认框。
        """.trimIndent()
        val result = NarrativeResult(
            narrative = narrative,
            actionCoverage = listOf(
                ActionCoverage("推进兑奖手续", "李静核对完登记信息")
            ),
            forwardHook = ForwardHook(
                cause = "林克确认了兑奖方式",
                evidence = "李静随即在银行转账一栏画了勾"
            ),
            endingScene = EndingScene("A07兑奖柜台", listOf("周明", "李静"))
        )

        val issue = NarrativeQualityPolicy.coverageIssue(
            result = result,
            userMessage = "继续推进当前目标",
            knownCharacters = listOf(
                CharacterCard(name = "周明", occupation = "柜台专员"),
                CharacterCard(name = "李静", occupation = "A07窗口工作人员")
            ),
            currentGoal = "确认兑奖方式",
            playerName = "林克"
        )

        assertTrue(issue?.contains("未获用户授权的重大决定") == true)
        assertTrue(NarrativeQualityPolicy.containsAgencyViolation(issue))
        assertTrue(!NarrativeQualityPolicy.isStyleOnlyIssue(issue))
    }

    @Test
    fun coverageIssue_allowsNpcToPresentConsequentialChoiceWithoutAnsweringForPlayer() {
        val narrative = """
            李静核对完登记信息，把兑奖方式确认单推到林克面前。纸上并列写着银行卡转账和其他兑现方式，后面各留着一处签名栏。

            “转入哪一个账户，需要您本人确认。”她把笔放在表格右侧，没有替他勾选。周明退开半步，柜台前只剩机器低低的运转声。

            屏幕弹出“等待持有人确认”，李静抬手示意林克查看两项说明，表格停在他面前。
        """.trimIndent()
        val result = NarrativeResult(
            narrative = narrative,
            actionCoverage = listOf(
                ActionCoverage("推进兑奖手续", "李静核对完登记信息")
            ),
            forwardHook = ForwardHook(
                cause = "兑现方式需要持有人亲自选择",
                evidence = "屏幕弹出“等待持有人确认”"
            ),
            endingScene = EndingScene("A07兑奖柜台", listOf("周明", "李静"))
        )

        assertNull(
            NarrativeQualityPolicy.coverageIssue(
                result = result,
                userMessage = "继续推进当前目标",
                knownCharacters = listOf(
                    CharacterCard(name = "周明", occupation = "柜台专员"),
                    CharacterCard(name = "李静", occupation = "A07窗口工作人员")
                ),
                currentGoal = "确认兑奖方式",
                playerName = "林克"
            )
        )
    }

    @Test
    fun coverageIssue_rejectsInterpretiveProcessProseFromRealContinuation() {
        val narrative = """
            穿着蓝色制服的柜台专员走近林克。这位专员名叫周明，他的步伐轻快而稳健，停在了距离林克的半步之遥处。他略微弯下腰，那张脸上带着标准的、训练有素的职业微笑，声音也显得温和且专业：“您好，请问有什么可以帮您的？是来兑奖、查询还是办理其他业务呢？”

            周明的这句询问如同一个精准的开关，立刻将林克从刚才那种被动的观望状态中抽离出来。他挺直了身体，感受着周围宏大空间带来的庄重感和制服所代表的秩序感。他回以一个略显激动但又迅速沉稳的笑容，语气清晰地回答：“你好，我来兑奖，中了彩票。”

            听到这个明确的目的后，周明微微颔首，眼中闪过了一丝高效的认可。他没有多做寒暄，而是侧身站位，用手指着不远处排队最靠前、并且显示有一个空位的柜台：“好的，兑奖业务请这边走。您看这个A07号柜台正好有专人接待，需要您先在这里登记一下基本信息。”他的引导是主动且流程化的。

            林克没有再多想，他立刻迈开了脚步。带着明确的目标和那份来自苏晚的期待，他开始跟随周明绕过几位正在等待业务的市民。每一步都踩得沉稳有力，目光始终锁定在A07号柜台的方向。当他逐渐靠近那个被指明的兑奖点时，周明似乎察觉到了他的动向，于是从身侧的一个文件托盘中取出一个崭新的、印有“福彩”标志的登记册，朝林克伸了过来。
        """.trimIndent()
        val result = NarrativeResult(
            narrative = narrative,
            actionCoverage = listOf(
                ActionCoverage("推进兑奖手续", "林克开始跟随周明绕过几位正在等待业务的市民")
            ),
            forwardHook = ForwardHook(
                cause = "周明开始办理登记手续",
                evidence = "周明似乎察觉到了他的动向"
            ),
            endingScene = EndingScene("省福彩中心A07柜台附近", listOf("周明")),
            introducedCharacters = listOf(
                IntroducedCharacter("周明", "福彩中心柜台专员", "初识", "周明走近林克")
            )
        )

        val issue = NarrativeQualityPolicy.coverageIssue(
            result = result,
            userMessage = "继续推进当前目标",
            knownCharacters = listOf(CharacterCard(name = "苏晚", occupation = "插画师")),
            currentGoal = "办理彩票兑奖与奖金领取手续"
        )

        assertTrue(issue?.contains("行为姿态") == true)
    }

    @Test
    fun coverageIssue_rejectsEditorialExplanationsAndRoundMetadataFromRealGemmaOutput() {
        val narrative = """
            林克迈开脚步，紧跟着周明绕过等待的市民群。他没有刻意加快速度，而是将步伐调整到一个恰到好处的、既不显得过于急躁，又不会显得漫无目的的节奏上，确保自己能清晰地观察到A07柜台的细节。

            “请这边走，林先生，”周明轻声提醒道，“您的身份证和银行卡准备好了吗？”周明的话语自然地将焦点从“抵达”转移到了“流程开始”。

            林克停下脚步，身体微微侧向周明。他感受到周明身上散发出的那种高效的、服务者的气息，这让他心中的兴奋感又被一层踏实的信任所覆盖。他最终决定直接回应：“是的，都准备好了。”他的语气不再是第6轮那种带着冲刺后的激动，而是更像一个获得了许可的参与者。

            周明似乎想进行一次简短的流程引导或交流。他把登记册推到林克面前，用指尖点了点第一栏：“请先填写姓名和身份证号码。”
        """.trimIndent()
        val result = NarrativeResult(
            narrative = narrative,
            actionCoverage = listOf(
                ActionCoverage("和周明聊聊", "林克停下脚步，身体微微侧向周明")
            ),
            forwardHook = ForwardHook(
                cause = "林克确认材料齐全",
                evidence = "周明似乎想进行一次简短的流程引导或交流"
            ),
            endingScene = EndingScene("A07柜台附近", listOf("周明"))
        )

        val issue = NarrativeQualityPolicy.coverageIssue(
            result = result,
            userMessage = "和周明聊聊",
            knownCharacters = listOf(CharacterCard(name = "周明", occupation = "柜台专员"))
        )

        assertTrue(issue?.contains("编辑式解释") == true)
        assertTrue(issue?.contains("应用元信息") == true)
    }

    @Test
    fun coverageIssue_rejectsSyntheticAbstractionFromSecondGemmaRepair() {
        val narrative = """
            林克没有丝毫犹豫地在登记册上写下姓名，动作稳健有力，如同机械般精确。那些数字在他手中迅速被锁定并落下。

            周明沉稳的气场似乎将那份亢奋压制了下来。他拿起笔，似乎是在等待下一步指令的接收者。

            文书工作结束后，林克觉得兴奋感终于找到了明确的宣泄口。队伍里的市民被流程推进所带动，一名妇女的眼神里流露出混合了羡慕与期待的复杂情绪。

            周明把流程的主导权和焦点完全交还给A07窗口。李静带着职业化的专注度看向林克，用平和的语气确认了接下来的行动指令。
        """.trimIndent()
        val result = NarrativeResult(
            narrative = narrative,
            actionCoverage = listOf(
                ActionCoverage("填写登记信息", "林克没有丝毫犹豫地在登记册上写下姓名")
            ),
            forwardHook = ForwardHook(
                cause = "登记信息填写完毕",
                evidence = "周明把流程的主导权和焦点完全交还给A07窗口"
            ),
            endingScene = EndingScene("A07窗口", listOf("周明", "李静"))
        )

        val issue = NarrativeQualityPolicy.coverageIssue(
            result = result,
            userMessage = "继续推进当前目标",
            knownCharacters = listOf(
                CharacterCard(name = "周明", occupation = "柜台专员"),
                CharacterCard(name = "李静", occupation = "A07窗口工作人员")
            ),
            currentGoal = "填写登记信息"
        )

        assertTrue(issue?.contains("抽象包装") == true)
    }

    @Test
    fun coverageIssue_acceptsConcreteRegistrationSceneWithoutEditorialConclusions() {
        val narrative = """
            林克在姓名栏写下名字，又照着身份证填完号码。他拿出手机核对最后四位，把手机号补进第三栏。

            周明接过登记册，从上到下扫了一遍，在证件号码旁画了一个勾。“身份证和中奖彩票给我。”他把两样东西放进透明文件袋，递向A07窗口。

            窗后的李静接过文件袋，将彩票平码朝上放进扫描槽。机器响了一声，屏幕跳出“等待复核”。她抬头看向林克：“号码录进去了，接下来要做一次人工复核，请在这里稍等。”
        """.trimIndent()
        val result = NarrativeResult(
            narrative = narrative,
            actionCoverage = listOf(
                ActionCoverage("填写登记信息", "林克在姓名栏写下名字")
            ),
            forwardHook = ForwardHook(
                cause = "登记信息和证件已提交",
                evidence = "屏幕跳出“等待复核”"
            ),
            endingScene = EndingScene("A07窗口", listOf("周明", "李静"))
        )

        val issue = NarrativeQualityPolicy.coverageIssue(
            result = result,
            userMessage = "继续推进当前目标",
            knownCharacters = listOf(
                CharacterCard(name = "周明", occupation = "柜台专员"),
                CharacterCard(name = "李静", occupation = "A07窗口工作人员")
            ),
            currentGoal = "填写登记信息"
        )

        assertNull(issue)
    }

    @Test
    fun coverageIssue_rejectsExpositoryNarrationFromThirdGemmaDraft() {
        val narrative = """
            李静的回应不再是一个简单的许可，而是一种正式的流程邀请。她的目光带着一种审视和确认的意味，扫过登记册，需要在视觉上确认信息是否完整就位。

            周明向前迈了一步，像是为流程提供额外的缓冲支持。他等待着李静启动下一步操作的信号。

            李静似乎拿起了内部平板。周明捕捉到了这个变化，这代表着登记阶段已经进入数据核验环节。他身体的放松度降低了一个档次，显示出一种专注的状态。

            李静带着职业性的聚焦感看向屏幕，将注意力集中在进度条上。登记册像是被工作状态激活一般，显得更加重要。
        """.trimIndent()
        val result = NarrativeResult(
            narrative = narrative,
            actionCoverage = listOf(
                ActionCoverage("推进核验", "李静似乎拿起了内部平板")
            ),
            forwardHook = ForwardHook(
                cause = "系统开始读取信息",
                evidence = "李静带着职业性的聚焦感看向屏幕"
            ),
            endingScene = EndingScene("A07窗口", listOf("周明", "李静"))
        )

        val issue = NarrativeQualityPolicy.coverageIssue(
            result = result,
            userMessage = "继续推进当前目标",
            knownCharacters = listOf(
                CharacterCard(name = "周明", occupation = "柜台专员"),
                CharacterCard(name = "李静", occupation = "A07窗口工作人员")
            ),
            currentGoal = "开始数据核验"
        )

        assertTrue(issue?.contains("说明书式旁白") == true)
    }

    @Test
    fun coverageIssue_rejectsHighPaddingDensityFromFourthGemmaDraft() {
        val narrative = """
            李静的目光紧锁在缓慢爬升的进度条上，手指微微收紧，指节泛起一层不易察觉的白痕。周明略微前倾，目光如同被吸住一般死死盯着屏幕，仿佛自己也能加速数据流转。

            等候的人群似乎都安静下来，空气中弥漫着一种集体屏息的氛围。林克保持着专注的姿态，将所有注意力投向柜台。他感觉兴奋感被等待过程拉伸、提纯，变得具体而可控。

            李静的身体出现轻微颤动，表情不再是职业化专注，而是带上一丝释然。她微微侧身，轻轻按下确认键。

            绿色对勾亮起，李静将目光投向林克，嘴角浮现出一个恰到好处、舒缓而得体的弧度。
        """.trimIndent()
        val result = NarrativeResult(
            narrative = narrative,
            actionCoverage = listOf(
                ActionCoverage("等待核验", "李静的目光紧锁在缓慢爬升的进度条上")
            ),
            forwardHook = ForwardHook(
                cause = "系统完成核验",
                evidence = "绿色对勾亮起"
            ),
            endingScene = EndingScene("A07窗口", listOf("周明", "李静"))
        )

        val issue = NarrativeQualityPolicy.coverageIssue(
            result = result,
            userMessage = "继续推进当前目标",
            knownCharacters = listOf(
                CharacterCard(name = "周明", occupation = "柜台专员"),
                CharacterCard(name = "李静", occupation = "A07窗口工作人员")
            ),
            currentGoal = "等待数据核验"
        )

        assertTrue(issue?.contains("修饰填充") == true)
    }

    @Test
    fun coverageIssue_rejectsNominalizedCommentaryFromFifthGemmaDraft() {
        val narrative = """
            李静按下确认键，目光转向林克，嘴角带着恰到好处的弧度，等待下一步指示。

            周明抬起手做出请的手势，比刚才更具引导性，催促流程进入下一环节。

            林克感到验证成功的松弛感从肩膀蔓延开来，微微颔首表示接受阶段性的批准。周明的语调带着高效的推进感，这是一种专业的服务引导。

            李静轻轻叩击柜台，这是标准的确认信号。她随后报出五百万元的奖金金额，请林克选择银行卡转账或其他兑现方式。
        """.trimIndent()
        val result = NarrativeResult(
            narrative = narrative,
            actionCoverage = listOf(
                ActionCoverage("推进核验", "李静按下确认键")
            ),
            forwardHook = ForwardHook(
                cause = "核验完成",
                evidence = "她随后报出五百万元的奖金金额"
            ),
            endingScene = EndingScene("A07窗口", listOf("周明", "李静"))
        )

        val issue = NarrativeQualityPolicy.coverageIssue(
            result = result,
            userMessage = "继续推进当前目标",
            knownCharacters = listOf(
                CharacterCard(name = "周明", occupation = "柜台专员"),
                CharacterCard(name = "李静", occupation = "A07窗口工作人员")
            ),
            currentGoal = "完成金额核验"
        )

        assertTrue(issue?.contains("名词化评价") == true)
    }

    @Test
    fun coverageIssue_rejectsRepeatedDialogueAndInterfaceChoreographyFromSixthGemmaDraft() {
        val previous = """
            李静请林克选择兑现方式。林克回答：“直接打款到我的储蓄卡上就好，谢谢。”

            李静在银行转账一栏画了勾，屏幕弹出等待提交的确认框。
        """.trimIndent()
        val narrative = """
            林克明确报出“直接打款到我的储蓄卡上就好，谢谢。”话音刚落，他身体紧绷的肌肉便随之松弛下来。李静拿起钢笔，在电子文档旁压下一个干净利落的圆圈，清晰地锁定了“银行转账”这一项。这个动作宣告了决策确认到流程执行指令的瞬间切换。

            李静将视线投向电脑屏幕中央的输入框，食指精准地按下确认键。系统开始接收并处理这笔五百万到储蓄卡的转移指令。

            周明的目光紧盯着屏幕上的数据流，身体微微侧向林克和李静的方向倾斜，像是在等待系统反馈的信号。

            加载指示条以一个稳定的、持续的速度向前推进，金额数字旁的校验文字开始闪烁。这标志着资金划拨指令已经进入实时验证和执行阶段。

            李静脸上的职业化表情终于松动了一丝，嘴角浮现出轻微的放松感。她再次按下锁定键，这相当于给系统发出了一个催促信号。
        """.trimIndent()
        val result = NarrativeResult(
            narrative = narrative,
            actionCoverage = listOf(
                ActionCoverage("推进已确认的转账手续", "李静将视线投向电脑屏幕中央的输入框")
            ),
            forwardHook = ForwardHook(
                cause = "系统开始执行已确认的转账",
                evidence = "金额数字旁的校验文字开始闪烁"
            ),
            endingScene = EndingScene("A07兑奖柜台", listOf("周明", "李静"))
        )

        val issue = NarrativeQualityPolicy.coverageIssue(
            result = result,
            userMessage = "继续推进当前目标",
            knownCharacters = listOf(
                CharacterCard(name = "周明", occupation = "柜台专员"),
                CharacterCard(name = "李静", occupation = "A07窗口工作人员")
            ),
            recentNarratives = listOf(NarrativeEntry(turn = 13, narrative = previous)),
            currentGoal = "启动转账手续（已确认银行转账）",
            playerName = "林克"
        )

        assertTrue(issue?.contains("重复上一轮已经说过的对白") == true)
        assertTrue(issue?.contains("界面流程解说") == true)
        assertTrue(issue?.contains("逐帧拆解") == true)
        assertTrue(!NarrativeQualityPolicy.containsAgencyViolation(issue))
        assertTrue(!NarrativeQualityPolicy.isStyleOnlyIssue(issue))
    }

    @Test
    fun coverageIssue_doesNotMistakeRepeatedAmountOrUiLabelForDialogue() {
        val previous = """
            李静按下“最终确认/锁定”键。屏幕上显示“5,000,000.00”，随后等待系统反馈。
        """.trimIndent()
        val narrative = """
            屏幕上的加载指示条以恒定速率推进，“5,000,000.00”数字旁的校验文字高速闪烁。这标志着资金划拨指令进入实时验证阶段。

            李静抬起手，精准地按在“最终确认/锁定”键。这个动作如同明确的指令，催促系统立刻给出反馈。

            进度线速度猛然提升，校验文字停止闪烁，稳定地锁定在绿色符号上。一个系统弹窗从操作台中央区域弹出。

            弹窗宣告资金已经汇入林克指定的储蓄卡账户。周明身体微微前倾，目光紧盯着屏幕上的数据流。

            李静转向林克：“林先生，您的奖金已成功到账，请在手机银行查询。”
        """.trimIndent()
        val result = NarrativeResult(
            narrative = narrative,
            actionCoverage = listOf(
                ActionCoverage("等待到账结果", "屏幕上的加载指示条以恒定速率推进")
            ),
            forwardHook = ForwardHook(
                cause = "系统完成资金划拨",
                evidence = "弹窗宣告资金已经汇入林克指定的储蓄卡账户"
            ),
            endingScene = EndingScene("A07兑奖柜台", listOf("周明", "李静"))
        )

        val issue = NarrativeQualityPolicy.coverageIssue(
            result = result,
            userMessage = "继续推进当前目标",
            knownCharacters = listOf(
                CharacterCard(name = "周明", occupation = "柜台专员"),
                CharacterCard(name = "李静", occupation = "A07窗口工作人员")
            ),
            recentNarratives = listOf(NarrativeEntry(turn = 14, narrative = previous)),
            currentGoal = "等待已确认转账的到账结果",
            playerName = "林克"
        )

        assertTrue(issue?.contains("重复上一轮已经说过的对白") != true)
        assertTrue(issue?.contains("界面流程解说") == true)
        assertTrue(NarrativeQualityPolicy.isStyleOnlyIssue(issue))
    }

    @Test
    fun blockingIssue_rejectsCriticalOmissionsButAllowsStyleWarnings() {
        assertTrue(
            NarrativeQualityPolicy.containsBlockingIssue(
                "主动续写必须出现在最后一个用户动作之后；用户引入的伴侣尚未获得真实姓名"
            )
        )
        assertFalse(
            NarrativeQualityPolicy.containsBlockingIssue(
                "正文有 7 处说明书式旁白；正文有 3 处逐帧拆解"
            )
        )
        assertFalse(NarrativeQualityPolicy.containsBlockingIssue(null))
    }
}
