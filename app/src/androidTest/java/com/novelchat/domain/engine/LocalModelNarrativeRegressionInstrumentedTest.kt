package com.novelchat.domain.engine

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.novelchat.data.model.CharacterNamingPolicy
import com.novelchat.data.model.NarrativeEntry
import com.novelchat.data.model.PlayerState
import com.novelchat.data.model.SummaryEntry
import com.novelchat.data.model.defaultModernCharacters
import com.novelchat.data.model.defaultModernStoryState
import com.novelchat.data.model.defaultModernWorldSetting
import com.novelchat.data.model.defaultModernWorldbookEntries
import com.novelchat.data.remote.AiService
import com.novelchat.data.storage.FileStorageManager
import com.novelchat.data.storage.SettingsRepository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalModelNarrativeRegressionInstrumentedTest {

    @Test
    fun gemma_expandsActionsAddsAftermathAndCommitsSecondAssignment() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val endpoint = arguments.getString("novelchat.localModelUrl").orEmpty().trim()
        val model = arguments.getString("novelchat.localModel").orEmpty().trim().ifBlank { "gemma4" }
        assumeTrue("本测试只在显式提供本地模型地址时执行", endpoint.isNotBlank())

        val context = instrumentation.targetContext
        val storage = FileStorageManager(context)
        val settings = SettingsRepository(context)
        settings.saveApiConnection(
            url = endpoint,
            key = "",
            model = model,
            currentProfileName = "Gemma4 Instrumented QA"
        )

        var storyId = 0L
        val action = "After safely settling my girlfriend at home I go downstairs to the cafe " +
            "buy a coffee and then visit the lottery shop to claim my 100 million yuan jackpot"
        try {
            storyId = storage.createConversationStorage()
            val createdAt = storage.getCurrentTimeString()
            storage.saveWorldSetting(storyId, defaultModernWorldSetting)
            storage.savePlayerState(
                storyId,
                PlayerState(
                    name = "林克",
                    class_ = "城市租客",
                    background = "租住在云桥里老小区，正在安排周一的现实事务。"
                )
            )
            defaultModernCharacters(createdAt).forEach { storage.saveCharacterCard(storyId, it) }
            defaultModernWorldbookEntries(createdAt).forEach { storage.saveWorldbookEntry(storyId, it) }
            storage.saveStoryState(
                storyId,
                defaultModernStoryState().copy(
                    currentRound = 1,
                    currentLocation = "云桥里老小区的出租屋",
                    activeCharacters = listOf("房东陈姐")
                )
            )
            storage.saveNarrative(
                storyId,
                NarrativeEntry(
                    turn = 1,
                    userInput = "开始这一天",
                    narrative = "周一早晨，林克在云桥里老小区的出租屋醒来。房东陈姐在门外敲门，手机里还留着临时会议和面试邀请。"
                )
            )
            storage.saveSummary(
                storyId,
                SummaryEntry(
                    turn = 1,
                    summary = "周一早晨，林克在出租屋面对房租、临时会议和面试邀请。"
                )
            )
            storage.completeConversationStorage(storyId)

            val events = withTimeout(360_000L) {
                RpgGameEngine(AiService(context), storage)
                    .executeRound(storyId, action)
                    .toList()
            }
            val errors = events.filterIsInstance<GameEvent.Error>()
            assertTrue("完整回合返回错误：${errors.joinToString { it.message }}", errors.isEmpty())
            assertTrue("第二次任务分配没有完成", events.any { it is GameEvent.TaskComplete })
            assertTrue(
                "回合没有完整提交",
                events.any { it is GameEvent.RoundComplete && it.round == 2 }
            )

            val narrative = storage.loadAllNarratives(storyId).single { it.turn == 2 }.narrative
            Log.i(TAG, "Gemma4 narrative: $narrative")
            assertTrue("正文没有使用主角真实姓名：$narrative", narrative.contains("林克"))
            assertTrue("正文没有完整承接女友安置：$narrative", Regex("女友|女朋友|伴侣").containsMatchIn(narrative))
            assertTrue("正文没有完整承接咖啡购买：$narrative", Regex("咖啡|拿铁|美式").containsMatchIn(narrative))
            assertTrue("正文没有完整承接一亿元兑奖：$narrative", Regex("一亿|100[，,]?000[，,]?000").containsMatchIn(narrative))
            assertTrue(
                "正文没有在兑奖后写出可观察反应或核验流程：$narrative",
                Regex(
                    "震惊|惊讶|愣|瞪大|倒吸|失声|手(?:指)?(?:停|抖)|反复核对|" +
                        "重新核对|二次(?:核验|复核)|主管|负责人|锁住终端|出示身份证|兑奖中心"
                ).containsMatchIn(narrative)
            )

            val characters = storage.loadAllCharacterCards(storyId)
            val partner = characters.firstOrNull { card ->
                listOf(
                    card.occupation,
                    card.background,
                    card.relationshipStatus,
                    card.relationships.values.joinToString { it.stage }
                ).any { Regex("女友|女朋友|伴侣").containsMatchIn(it) }
            }
            assertNotNull("第二次任务分配没有为用户引入的女友创建角色卡", partner)
            assertFalse(
                "女友角色仍使用身份占位名：${partner?.name}",
                CharacterNamingPolicy.isGenericNpcName(partner?.name.orEmpty())
            )
            val lotteryWorker = characters.firstOrNull { card ->
                Regex("彩票|兑奖").containsMatchIn(card.occupation + card.background)
            }
            assertNotNull("第二次任务分配没有创建具名兑奖工作人员角色卡", lotteryWorker)
            assertFalse(
                "兑奖工作人员仍使用身份占位名：${lotteryWorker?.name}",
                CharacterNamingPolicy.isGenericNpcName(lotteryWorker?.name.orEmpty())
            )

            val state = storage.loadStoryState(storyId)
            Log.i(TAG, "Gemma4 roster: ${characters.joinToString { "${it.name}/${it.occupation}" }}")
            assertTrue("故事状态未推进到第 2 轮", state.currentRound == 2)
            assertTrue("结尾地点没有同步到兑奖现场：${state.currentLocation}", Regex("彩票|兑奖").containsMatchIn(state.currentLocation))
            assertTrue("第二次任务分配后仍残留待补同步标记", StateSyncPolicy.pendingFromRound(state) == null)
        } finally {
            if (storyId > 0L) {
                storage.deleteConversation(storyId)
            }
        }
    }

    companion object {
        private const val TAG = "LocalModelNarrativeQA"
    }
}
