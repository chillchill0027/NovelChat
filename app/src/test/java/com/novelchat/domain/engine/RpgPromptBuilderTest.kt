package com.novelchat.domain.engine

import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.IntroducedCharacter
import com.novelchat.data.model.PlayerState
import com.novelchat.data.model.NarrativeEntry
import com.novelchat.data.model.StoryState
import com.novelchat.data.model.StoryProgress
import com.novelchat.data.model.SummaryEntry
import com.novelchat.data.model.defaultModernWorldSetting
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RpgPromptBuilderTest {

    private val builder = RpgPromptBuilder()

    @Test
    fun taskPrompt_usesValidCompactDynamicStateSchema() {
        val prompt = builder.buildTaskAssignmentPrompt(
            worldSetting = defaultModernWorldSetting,
            playerState = null,
            storyState = StoryState(currentLocation = "云桥里老小区"),
            activeCharacters = listOf(
                CharacterCard(
                    id = "npc_1",
                    name = "同事林越",
                    libido = 62,
                    threshold = 48,
                    kink = "担心项目延期"
                )
            ),
            worldbookEntries = emptyList(),
            narrative = "林越发来会议消息。",
            summary = "周一早晨出现工作压力。",
            recentNarratives = emptyList(),
            introducedCharacters = listOf(
                IntroducedCharacter("老周", "彩票店老板", "初识", "老周核验彩票")
            )
        )

        assertTrue(prompt.contains("story_update"))
        assertTrue(prompt.contains("physical_state"))
        assertTrue(prompt.contains("mental_state"))
        assertTrue(prompt.contains("输出模板"))
        assertFalse(prompt.contains("..."))
        assertFalse(prompt.contains("action_drive"))
        assertFalse(prompt.contains("caution_threshold"))
        assertFalse(prompt.contains("hidden_clue"))
        assertFalse(prompt.contains("\"libido\""))
        assertFalse(prompt.contains("\"kink\""))
        assertTrue(prompt.contains("真实姓名"))
        assertTrue(prompt.contains("禁止写成字符串数组"))
        assertTrue(prompt.contains("\"description\":\"本轮已发生的事实\""))
        assertTrue(prompt.contains("risk 必须是 0-100 的整数"))
        assertTrue(prompt.contains("player_update 不得为 null"))
        assertTrue(prompt.contains("current_goal"))
        assertTrue(prompt.contains("不要留下已经完成的旧目标"))
        assertTrue(prompt.contains("删除前文中已完成的事项"))
        assertTrue(prompt.contains("开始处理、形成初稿、取得部分回复或约定未来时间都只是进展"))
        assertTrue(prompt.contains("不等于未来的面试/会议已经完成"))
        assertTrue(prompt.contains("本轮首次登场人物锚点"))
        assertTrue(prompt.contains("老周"))
        assertTrue(prompt.contains("不能引用不存在的角色卡"))
        assertTrue(prompt.contains("sync_status"))
        assertTrue(prompt.contains("no_change_reason"))
    }

    @Test
    fun characterStatePrompt_requestsIncrementalDecisionUsefulState() {
        val prompt = builder.buildCharacterStatePrompt(
            worldSetting = defaultModernWorldSetting,
            character = CharacterCard(name = "招聘顾问孟琪", libido = 55, threshold = 70, kink = "评估候选人稳定性"),
            recentNarratives = emptyList(),
            narrative = "孟琪说明岗位风险。",
            userMessage = "我询问薪资结构。"
        )

        assertTrue(prompt.contains("增量补丁"))
        assertTrue(prompt.contains("favor_delta"))
        assertTrue(prompt.contains("physical_state"))
        assertTrue(prompt.contains("mental_state"))
        assertFalse(prompt.contains("action_drive"))
        assertFalse(prompt.contains("caution_threshold"))
        assertFalse(prompt.contains("hidden_clue"))
        assertFalse(prompt.contains("\"libido\""))
        assertFalse(prompt.contains("\"kink\""))
        assertFalse(prompt.contains("\"rarity\""))
    }

    @Test
    fun characterStatePrompt_includesRecoveryContextAndUpdateReason() {
        val prompt = builder.buildCharacterStatePrompt(
            worldSetting = defaultModernWorldSetting,
            character = CharacterCard(name = "彩票店老板老周"),
            recentNarratives = emptyList(),
            narrative = "老周重新核对了兑奖材料。",
            userMessage = "继续办理兑奖。",
            playerName = "林克",
            updateReason = "老周得知奖金金额后十分震惊",
            recoveryNarratives = listOf(
                NarrativeEntry(
                    turn = 2,
                    userInput = "去彩票店兑奖",
                    narrative = "老周看见一亿元的中奖结果，手指停在键盘上。"
                )
            )
        )

        assertTrue(prompt.contains("本次更新原因"))
        assertTrue(prompt.contains("老周得知奖金金额后十分震惊"))
        assertTrue(prompt.contains("待补同步的角色相关剧情"))
        assertTrue(prompt.contains("一亿元的中奖结果"))
        assertTrue(prompt.contains("不得重复累计"))
    }

    @Test
    fun narrativePrompt_expandsEveryUserActionAndAddsForwardHook() {
        val prompt = builder.buildNarrativePrompt(
            worldSetting = defaultModernWorldSetting,
            playerState = PlayerState(name = "林克", class_ = "城市租客"),
            activeCharacters = emptyList(),
            worldbookEntries = emptyList(),
            currentWorldbook = null,
            relatedWorldbook = emptyList(),
            storyState = StoryState(currentLocation = "云桥里老小区"),
            allSummaries = emptyList(),
            recentNarratives = emptyList(),
            userMessage = "安置好女朋友之后，去楼下咖啡厅买一杯咖啡，去彩票店兑奖。"
        )

        assertTrue(prompt.contains("每个动作"))
        assertTrue(prompt.contains("3-6段"))
        assertTrue(prompt.contains("1000-1500 个中文字符"))
        assertTrue(prompt.contains("主动续写"))
        assertTrue(prompt.contains("每句话只承担一种作用"))
        assertTrue(prompt.contains("同一个动作只写一次"))
        assertTrue(prompt.contains("屏幕与业务流程只写会改变局面的最终反馈"))
        assertTrue(prompt.contains("禁止逐帧描写按键、光标、进度条、数据流"))
        assertTrue(prompt.contains("潜台词"))
        assertTrue(prompt.contains("先在内部完成整段小说正文"))
        assertTrue(prompt.contains("不能反过来把正文写成答题或清单"))
        assertTrue(prompt.contains("不要在开头换几个词复述用户输入"))
        assertTrue(prompt.contains("可观察反应"))
        assertTrue(prompt.contains("正文中的人物不知道轮次、玩家、提示词或应用状态"))
        assertFalse(prompt.contains("精准的开关"))
        assertFalse(prompt.contains("服务者的气息"))
        assertTrue(prompt.contains("一亿元"))
        assertTrue(prompt.contains("震惊"))
        assertTrue(prompt.contains("action_coverage"))
        assertTrue(prompt.contains("forward_hook"))
        assertTrue(prompt.contains("最后一个动作之后"))
        assertTrue(prompt.contains("逐字摘录"))
        assertTrue(prompt.contains("无字段名的字符串"))
        assertTrue(prompt.contains("必须逐项落实的动作清单"))
        assertTrue(prompt.contains("1. 安置好女朋友之后"))
        assertTrue(prompt.contains("2. 去楼下咖啡厅买一杯咖啡"))
        assertTrue(prompt.contains("3. 去彩票店兑奖"))
        assertTrue(prompt.contains("资料中没有就静默删去"))
        assertTrue(prompt.contains("ending_scene"))
        assertTrue(prompt.contains("introduced_characters"))
        assertTrue(prompt.contains("安置好/照顾好某人"))
        assertTrue(prompt.contains("不能把完成态改成打算、通知或准备"))
        assertTrue(prompt.contains("\"occupation\":\"城市租客\""))
        assertTrue(prompt.contains("\"current_risk\""))
        assertFalse(prompt.contains("\"hunting_skill\""))
        assertFalse(prompt.contains("\"alpha\""))
        assertFalse(prompt.contains("\"cv\""))
    }

    @Test
    fun narrativePrompt_includesResolvedIdentityAnchor() {
        val prompt = builder.buildNarrativePrompt(
            worldSetting = defaultModernWorldSetting,
            playerState = PlayerState(name = "林克"),
            activeCharacters = emptyList(),
            worldbookEntries = emptyList(),
            currentWorldbook = null,
            relatedWorldbook = emptyList(),
            storyState = StoryState(currentLocation = "出租屋"),
            allSummaries = emptyList(),
            recentNarratives = emptyList(),
            userMessage = "安置好女朋友之后去买咖啡",
            identityAnchors = listOf(
                NarrativeIdentityAnchor(
                    reference = "女友",
                    name = "苏晚",
                    relationship = "女友",
                    aliases = listOf("女朋友", "女友", "girlfriend"),
                    isExistingCharacter = false
                )
            )
        )

        assertTrue(prompt.contains("本轮人物实名锚点"))
        assertTrue(prompt.contains("女友苏晚"))
        assertTrue(prompt.contains("必须写入 introduced_characters"))
        assertTrue(prompt.contains("不能改名、删去或退回身份泛称"))
    }

    @Test
    fun narrativePrompt_turnsContinueRequestIntoConcreteGoalProgress() {
        val prompt = builder.buildNarrativePrompt(
            worldSetting = defaultModernWorldSetting,
            playerState = PlayerState(
                name = "林克",
                storyProgress = StoryProgress(currentGoal = "办理彩票兑奖与奖金领取手续")
            ),
            activeCharacters = emptyList(),
            worldbookEntries = emptyList(),
            currentWorldbook = null,
            relatedWorldbook = emptyList(),
            storyState = StoryState(currentLocation = "主干道人行道"),
            allSummaries = listOf(
                SummaryEntry(
                    turn = 5,
                    summary = "林克已经向王奶奶道别，并走到省福彩中心门前。"
                )
            ),
            recentNarratives = listOf(
                NarrativeEntry(
                    turn = 5,
                    narrative = "顾宁说：“您的材料已经登记完成。”林克随后走到省福彩中心复核窗口。"
                )
            ),
            userMessage = "继续推进当前目标"
        )

        assertTrue(prompt.contains("【续写边界】"))
        assertTrue(prompt.contains("上一轮最后一句之后的下一刻"))
        assertTrue(prompt.contains("当前目标：办理彩票兑奖与奖金领取手续"))
        assertTrue(prompt.contains("不能只写继续赶路"))
        assertTrue(prompt.contains("在主角回答之前收束"))
        assertTrue(prompt.contains("禁止替主角回答、点头、签字或执行选择"))
        assertTrue(prompt.contains("普通走路、乘车、等待和回想应压缩成一句"))
        assertTrue(prompt.contains("1. 推动当前目标取得实质进展：办理彩票兑奖与奖金领取手续"))
        assertTrue(prompt.contains("上一轮已完成事实（只作为续写起点，不得复述原句）"))
        assertTrue(prompt.contains("林克已经向王奶奶道别，并走到省福彩中心门前"))
        assertFalse(prompt.contains("您的材料已经登记完成"))
        assertFalse(prompt.contains("上一轮收束句"))
        assertFalse(prompt.contains("【最近对话事实，只用于连续性，不模仿措辞】"))
        assertFalse(prompt.contains("使命感、使命驱动"))
    }

    @Test
    fun taskRepairPrompt_containsOnlyLegalJsonTemplate() {
        val prompt = builder.buildTaskAssignmentRepairPrompt("任务如下：{\"events\":[,]}")

        assertTrue(prompt.contains("合法 JSON"))
        assertTrue(prompt.contains("story_update"))
        assertTrue(prompt.contains("不能是纯字符串"))
        assertTrue(prompt.contains("原文字放进 description"))
        assertTrue(prompt.contains("删除 risk"))
        assertFalse(prompt.contains("{...}"))
    }

    @Test
    fun taskPrompt_includesUnsyncedNarrativesForRecovery() {
        val storyState = StateSyncPolicy.markPending(
            StoryState(currentRound = 3, currentLocation = "彩票店"),
            2
        )
        val prompt = builder.buildTaskAssignmentPrompt(
            worldSetting = defaultModernWorldSetting,
            playerState = PlayerState(name = "林克"),
            storyState = storyState,
            activeCharacters = emptyList(),
            worldbookEntries = emptyList(),
            narrative = "林克办完手续走出彩票店。",
            summary = "林克完成兑奖手续。",
            recentNarratives = listOf(
                NarrativeEntry(turn = 1, narrative = "无关旧剧情"),
                NarrativeEntry(turn = 2, userInput = "去兑奖", narrative = "林克递出彩票。")
            )
        )

        assertTrue(prompt.contains("待补同步剧情"))
        assertTrue(prompt.contains("第2轮：用户=去兑奖"))
        assertFalse(prompt.contains("无关旧剧情"))
    }

    @Test
    fun essentialStateSyncPrompt_usesCompactCompleteSchema() {
        val prompt = builder.buildEssentialStateSyncPrompt(
            playerState = PlayerState(name = "林克"),
            storyState = StoryState(currentLocation = "咖啡厅"),
            activeCharacters = listOf(CharacterCard(name = "顾宁", occupation = "店员")),
            narrative = "顾宁递来咖啡。",
            summary = "林克买到咖啡。",
            recentNarratives = emptyList()
        )

        assertTrue(prompt.contains("必要状态同步"))
        assertTrue(prompt.contains("pending_events"))
        assertTrue(prompt.contains("new_characters"))
        assertTrue(prompt.contains("\"events\":[]"))
        assertTrue(prompt.contains("本轮首次登场人物锚点"))
    }

    @Test
    fun narrativeRepairPrompt_requiresFullRewriteInsteadOfSummaryPatch() {
        val prompt = builder.buildNarrativeRepairPrompt(
            originalPrompt = "原始提示",
            rawResponse = "{\"narrative\":\"只写了第一个动作\"",
            issue = "输出不是可解析的完整 JSON"
        )

        assertTrue(prompt.contains("必须重写"))
        assertTrue(prompt.contains("不要只修 summary"))
        assertTrue(prompt.contains("重新输出完整 JSON"))
        assertTrue(prompt.contains("输出不是可解析的完整 JSON"))
        assertTrue(prompt.contains("{\"narrative\":\"只写了第一个动作\""))
        assertTrue(prompt.contains("独立 evidence"))
        assertTrue(prompt.contains("forward_hook"))
        assertTrue(prompt.contains("ending_scene"))
        assertTrue(prompt.contains("introduced_characters"))
        assertTrue(prompt.contains("先把 narrative 重写成自然连贯的小说场景"))
        assertTrue(prompt.contains("不要逐条复述动作清单"))
        assertTrue(prompt.contains("【现在重写】"))
        assertTrue(prompt.lastIndexOf("【现在重写】") > prompt.lastIndexOf("只写了第一个动作"))
        assertTrue(prompt.contains("不得复用上次输出或上一轮中的整句"))
        assertTrue(prompt.contains("再做一次删句检查"))
        assertTrue(prompt.contains("1000-1500 个中文字符"))
    }

    @Test
    fun narrativeFocusedRepairPrompt_turnsRemainingIssuesIntoConcreteChecks() {
        val prompt = builder.buildNarrativeFocusedRepairPrompt(
            rawResponse = """{"narrative":"林克把一亿元中奖彩票递给店员。"}""",
            issue = "用户明确的高影响结果发生后，正文没有在最后动作之后写出当事人的可观察反应或核验流程；用户引入的伴侣尚未获得真实姓名和 introduced_characters 记录",
            userMessage = "安置好女朋友后去彩票店领取一亿元奖金",
            playerName = "林克"
        )

        assertTrue(prompt.contains("终审编辑"))
        assertTrue(prompt.contains("林克"))
        assertTrue(prompt.contains("最后一个用户动作后必须另起一段"))
        assertTrue(prompt.contains("具体可观察震惊反应"))
        assertTrue(prompt.contains("一亿元（人民币100,000,000元）"))
        assertTrue(prompt.contains("禁止改成一百万元、一千万元"))
        assertTrue(prompt.contains("具名兑奖人员"))
        assertTrue(prompt.contains("1000-1500 个中文字符"))
        assertTrue(prompt.contains("伴侣/亲属"))
        assertTrue(prompt.contains("introduced_characters"))
        assertTrue(prompt.contains("1000-1500 个中文字符"))
        assertTrue(prompt.contains("不得原样返回"))
        assertTrue(prompt.contains("待修复 JSON"))
        assertFalse(prompt.contains("【世界整体设定】"))
    }

    @Test
    fun narrativeStyleRepairPrompt_isCompactAndPreservesFacts() {
        val prompt = builder.buildNarrativeStyleRepairPrompt(
            rawResponse = """{"narrative":"林克把登记册递给李静。","summary":"进入核验。"}""",
            issue = "正文有 7 处说明书式旁白"
        )

        assertTrue(prompt.contains("中文小说删改编辑"))
        assertTrue(prompt.contains("不得新增、删除或改变任何剧情事实"))
        assertTrue(prompt.contains("每句话至少带来一个可看到、听到、触到的动作"))
        assertTrue(prompt.contains("屏幕反馈只保留最终变化"))
        assertTrue(prompt.contains("所有引号内对白及其说话者必须原样保留"))
        assertTrue(prompt.contains("不得新增主角对白"))
        assertTrue(prompt.contains("林克把登记册递给李静"))
        assertTrue(prompt.contains("action_coverage"))
        assertTrue(prompt.contains("introduced_characters"))
        assertTrue(!prompt.contains("【世界整体设定】"))
    }
}
