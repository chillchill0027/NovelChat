package com.novelchat.domain.v2.context

import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.CharacterLore
import com.novelchat.data.model.NarrationSettings
import com.novelchat.data.model.NarrativeEntry
import com.novelchat.data.model.PhysicalState
import com.novelchat.data.model.PlayerState
import com.novelchat.data.model.StoryState
import com.novelchat.data.model.SummaryEntry
import com.novelchat.data.model.WorldSetting
import com.novelchat.data.model.WorldbookEntry
import com.novelchat.data.repo.StorySnapshot
import com.novelchat.domain.v2.memory.ChapterSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextAssemblerTest {

    private fun baseSnapshot(
        storyState: StoryState = StoryState(
            currentRound = 3,
            currentLocation = "云桥里",
            timeOfDay = "傍晚",
            weather = "小雨",
            activeCharacters = listOf("房东陈姐"),
            pendingEvents = listOf("周五前交房租", "回复林越")
        ),
        stamina: Int = 90,
        narratives: List<NarrativeEntry> = listOf(
            NarrativeEntry(turn = 1, userInput = "出门", narrative = "第一轮正文。"),
            NarrativeEntry(turn = 2, userInput = "买咖啡", narrative = "第二轮正文。"),
            NarrativeEntry(turn = 3, userInput = "回小区", narrative = "第三轮正文。")
        )
    ): StorySnapshot = StorySnapshot(
        storyId = 1L,
        world = WorldSetting(name = "海临市", description = "现代都市模拟"),
        story = storyState,
        player = PlayerState(
            name = "林克",
            class_ = "租客",
            personality = "冷静务实",
            physicalState = PhysicalState(stamina = stamina)
        ),
        characters = listOf(
            CharacterCard(id = "chen", name = "房东陈姐", occupation = "房东"),
            CharacterCard(
                id = "lin",
                name = "同事林越",
                occupation = "项目执行",
                lore = CharacterLore(secrets = listOf("准备跳槽"))
            )
        ),
        worldbook = listOf(
            WorldbookEntry(
                id = "wb1",
                type = "location",
                name = "云桥里",
                description = "老小区",
                keywords = listOf("云桥里")
            ),
            WorldbookEntry(
                id = "wb2",
                type = "item",
                name = "地下档案",
                description = "藏着旧合同",
                keywords = listOf("合同"),
                isSecret = true,
                knownByPlayer = false
            )
        ),
        narratives = narratives,
        summaries = listOf(
            SummaryEntry(turn = 1, summary = "林克出门。"),
            SummaryEntry(turn = 2, summary = "林克买了咖啡。"),
            SummaryEntry(turn = 3, summary = "林克回到小区。")
        ),
        chapters = emptyList(),
        narration = NarrationSettings()
    )

    @Test
    fun systemPrompt_staysByteIdenticalAcrossDynamicStateChanges() {
        val snap1 = baseSnapshot(
            storyState = StoryState(currentRound = 3, currentLocation = "云桥里", weather = "晴", activeCharacters = listOf("房东陈姐")),
            stamina = 95
        )
        val snap2 = baseSnapshot(
            storyState = StoryState(currentRound = 4, currentLocation = "近岸商务区", weather = "暴雨", activeCharacters = listOf("同事林越")),
            stamina = 42
        )

        val ctx1 = ContextAssembler.assemble(snap1, PlayerInput("查看手机"), ContextBudget.REMOTE)
        val ctx2 = ContextAssembler.assemble(snap2, PlayerInput("回复合同"), ContextBudget.REMOTE)

        assertEquals(ctx1.systemPrompt, ctx2.systemPrompt)
        val msgs = ctx2.toMessages()
        assertEquals("system", msgs.first().role)
        assertEquals("user", msgs.last().role)
        assertTrue(msgs[1].role == "user" && msgs[2].role == "assistant")
    }

    @Test
    fun localBudget_retainsLatestTurnAndTrimsOlderHistoryAndContext() {
        val longNarratives = (1..6).map { turn ->
            NarrativeEntry(
                turn = turn,
                userInput = "第${turn}轮行动",
                narrative = "第${turn}轮正文" + "内容".repeat(700)
            )
        }
        val snap = baseSnapshot(narratives = longNarratives).copy(
            chapters = listOf(ChapterSummary(index = 1, fromTurn = 1, toTurn = 2, text = "前两轮章节梗概"))
        )

        val ctx = ContextAssembler.assemble(snap, PlayerInput("敲门询问合同"), ContextBudget.LOCAL)

        assertTrue(ctx.history.size >= 2)
        assertTrue(ctx.history.last().content.contains("第6轮正文") || ctx.history.last().content.startsWith("…"))
        assertFalse(ctx.history.any { it.content.contains("第1轮正文") })
        assertTrue(ctx.turnContext.contains("地下档案（作者备注，角色不会主动透露）"))
        assertTrue(ctx.debug.historyTurnsIncluded < 6)
    }

    @Test
    fun openingTurn_includesSceneDescriptionInTurnContext() {
        val snap = baseSnapshot(
            storyState = StoryState(
                currentLocation = "云桥里 1203",
                sceneDescription = "纸箱沿墙边排开，桌上放着最后一把钥匙。"
            ),
            narratives = emptyList()
        ).copy(summaries = emptyList())

        val ctx = ContextAssembler.assemble(snap, PlayerInput("开始这一天"), ContextBudget.REMOTE)

        assertTrue(ctx.turnContext.contains("纸箱沿墙边排开，桌上放着最后一把钥匙。"))
        assertTrue(ctx.history.isEmpty())
    }
}
