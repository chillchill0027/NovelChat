package com.novelchat.session

import com.novelchat.data.model.NarrativeEntry
import com.novelchat.data.model.PlayerState
import com.novelchat.data.model.StoryState
import com.novelchat.data.model.WorldSetting
import com.novelchat.data.remote.AiCallStage
import com.novelchat.data.remote.llm.LlmClient
import com.novelchat.data.remote.llm.LlmCompletion
import com.novelchat.data.remote.llm.LlmMetrics
import com.novelchat.data.remote.llm.LlmRequest
import com.novelchat.data.remote.llm.LlmStreamEvent
import com.novelchat.data.repo.StoryRepository
import com.novelchat.data.storage.FileStorageManager
import com.novelchat.domain.engine.StateSyncPolicy
import com.novelchat.domain.v2.context.PlayerInput
import com.novelchat.domain.v2.memory.MemoryManager
import com.novelchat.domain.v2.narrator.NarratorAgent
import com.novelchat.domain.v2.round.RoundEvent
import com.novelchat.domain.v2.round.RoundOrchestrator
import com.novelchat.domain.v2.state.StateKeeperAgent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CopyOnWriteArrayList

class StorySessionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeSessionLlmClient(
        private val streamHandler: suspend (Int, LlmRequest) -> List<LlmStreamEvent>,
        private val completeHandler: suspend (Int, LlmRequest) -> String
    ) : LlmClient {
        val streamRequests = CopyOnWriteArrayList<LlmRequest>()
        val completeRequests = CopyOnWriteArrayList<LlmRequest>()

        override fun stream(request: LlmRequest): Flow<LlmStreamEvent> = flow {
            val idx = streamRequests.size
            streamRequests += request
            val events = streamHandler(idx, request)
            events.forEach { emit(it) }
        }

        override suspend fun complete(request: LlmRequest): LlmCompletion {
            val idx = completeRequests.size
            completeRequests += request
            val text = completeHandler(idx, request)
            return LlmCompletion(text = text, finishReason = "stop")
        }
    }

    private fun prose(tag: String): String =
        "${tag}清晨的微风拂过海临市的街头，林克推开公寓楼下的玻璃门，" +
            "走进街角那家二十四小时营业的便利店。柜台后的暖灯亮着，" +
            "咖啡机正发出轻微的蒸汽声响，窗外天色渐亮，行人开始三三两两地经过路口。"

    private suspend fun createStory(
        stateFlags: Map<String, String> = emptyMap(),
        existingNarratives: List<NarrativeEntry> = emptyList(),
        currentRound: Int = existingNarratives.size
    ): Pair<FileStorageManager, Long> {
        val files = FileStorageManager.forDirectory(tempFolder.root)
        val storyId = files.createConversationStorage()
        files.saveWorldSetting(storyId, WorldSetting(name = "海临市", description = "近未来都市测试世界"))
        files.savePlayerState(storyId, PlayerState(name = "林克"))
        files.saveStoryState(
            storyId,
            StoryState(
                currentRound = currentRound,
                currentLocation = "云桥里公寓",
                globalFlags = stateFlags
            )
        )
        existingNarratives.forEach { files.saveNarrative(storyId, it) }
        files.completeConversationStorage(storyId)
        return files to storyId
    }

    private fun newSession(
        files: FileStorageManager,
        storyId: Long,
        scope: CoroutineScope,
        llmClient: LlmClient,
        awaitPreviousSyncTimeoutMs: Long = 50L
    ): Pair<StoryRepository, StorySession> {
        val repo = StoryRepository(files)
        val metrics = LlmMetrics(logSink = {})
        val session = StorySession(
            storyId = storyId,
            scope = scope,
            repo = repo,
            orchestrator = RoundOrchestrator(repo, NarratorAgent(llmClient), metrics) {
                "2026-11-26T12:00:00"
            },
            stateKeeper = StateKeeperAgent(llmClient),
            memoryManager = MemoryManager(llmClient),
            isLocalNarrative = { false },
            isLocalStateKeeper = { false },
            awaitPreviousSyncTimeoutMs = awaitPreviousSyncTimeoutMs,
            clockNow = { "2026-11-26T12:00:00" }
        )
        return repo to session
    }

    @Test
    fun r1_r2_r3_provisionalCheckpointGetsPatchedAfterPriorSyncFinishes() = runBlocking {
        val (files, storyId) = createStory()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val syncRound1Started = CompletableDeferred<Unit>()
        val releaseSyncRound1 = CompletableDeferred<Unit>()

        val client = FakeSessionLlmClient(
            streamHandler = { idx, _ ->
                val text = prose("第${idx + 1}轮")
                listOf(
                    LlmStreamEvent.Delta(text.take(30)),
                    LlmStreamEvent.Delta(text.drop(30)),
                    LlmStreamEvent.Finished(finishReason = "stop")
                )
            },
            completeHandler = { idx, _ ->
                if (idx == 0) {
                    syncRound1Started.complete(Unit)
                    releaseSyncRound1.await()
                    """{"scene":{"location":"望潮路便利店"},"summary":"林克出门来到望潮路便利店","suggestions":["买热咖啡"]}"""
                } else {
                    """{"summary":"林克在便利店买了一杯咖啡","suggestions":["回公寓"]}"""
                }
            }
        )

        val (repo, session) = newSession(files, storyId, scope, client, awaitPreviousSyncTimeoutMs = 40L)
        try {
            // 第 1 轮：正文流式输出立刻提交且界面可继续输入（R1），后台状态同步仍被闸门挂起
            session.startRound(PlayerInput("出门去便利店"))
            withTimeout(5_000) {
                session.roundEvents.first { it is RoundEvent.NarrativeCommitted && it.turn == 1 }
            }
            withTimeout(5_000) { syncRound1Started.await() }
            assertEquals(RoundPhase.IDLE, session.state.value.phase)

            // 第 2 轮：在等待第 1 轮超时后直接开始，保存第 2 轮 provisional=true 检查点（R2）
            session.startRound(PlayerInput("点一杯热美式"))
            withTimeout(5_000) {
                session.roundEvents.first { it is RoundEvent.NarrativeCommitted && it.turn == 2 }
            }
            val unpatchedCheckpoint = files.loadRoundCheckpoint(storyId, 2)!!
            assertTrue(unpatchedCheckpoint.provisional)
            assertEquals("云桥里公寓", unpatchedCheckpoint.storyState.currentLocation)

            // 放行第 1 轮同步，第 2 轮随后也同步完成；验证第 2 轮检查点被补写为最新地点且取消 provisional（R3）
            releaseSyncRound1.complete(Unit)
            assertTrue(withTimeout(5_000) { session.awaitSyncIdle(5_000) })

            val patchedCheckpoint = files.loadRoundCheckpoint(storyId, 2)!!
            assertFalse(patchedCheckpoint.provisional)
            assertEquals("望潮路便利店", patchedCheckpoint.storyState.currentLocation)
            assertEquals(2, repo.snapshot(storyId).summaries.size)
        } finally {
            session.close()
            scope.cancel()
        }
    }

    @Test
    fun r8_failedSyncDoesNotBlockNextTurnAndAutoCatchesUpUpToThreeRounds() = runBlocking {
        val (files, storyId) = createStory()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        val client = FakeSessionLlmClient(
            streamHandler = { idx, _ ->
                val text = prose("第${idx + 1}轮")
                listOf(
                    LlmStreamEvent.Delta(text),
                    LlmStreamEvent.Finished(finishReason = "stop")
                )
            },
            completeHandler = { idx, _ ->
                // 第 1 轮的 StateKeeper 连续两次解析失败 -> 第 1 轮后台同步失败
                if (idx <= 1) {
                    "not valid json"
                } else {
                    """{"scene":{"location":"海临书店"},"summary":"补同步完成第1、2轮","suggestions":["逛书架"]}"""
                }
            }
        )

        val (repo, session) = newSession(files, storyId, scope, client, awaitPreviousSyncTimeoutMs = 40L)
        try {
            session.startRound(PlayerInput("先去街角转转"))
            withTimeout(5_000) {
                session.roundEvents.first { it is RoundEvent.NarrativeCommitted && it.turn == 1 }
            }
            withTimeout(5_000) {
                session.state.first { 1 in it.sync.failedRounds }
            }
            // R8: 状态同步失败后依然可以继续发送下一轮
            assertEquals(RoundPhase.IDLE, session.state.value.phase)

            session.startRound(PlayerInput("走进海临书店"))
            withTimeout(5_000) {
                session.roundEvents.first { it is RoundEvent.NarrativeCommitted && it.turn == 2 }
            }
            assertTrue(withTimeout(5_000) { session.awaitSyncIdle(5_000) })

            withTimeout(5_000) {
                session.state.first { it.sync.failedRounds.isEmpty() && it.sync.lastSyncedRound == 2 }
            }
            val keeperReqForCatchup = client.completeRequests.last { it.stage == AiCallStage.STATE_KEEPER }
            val userMsg = keeperReqForCatchup.messages.last().content
            assertTrue(userMsg.contains("第 1 轮") || userMsg.contains("先去街角转转"))
            assertTrue(userMsg.contains("第 2 轮") || userMsg.contains("走进海临书店"))
            assertEquals("海临书店", repo.snapshot(storyId).story.currentLocation)
        } finally {
            session.close()
            scope.cancel()
        }
    }

    @Test
    fun r9_regenerateCancelsFromTurnWaitsForEarlierSyncAndRestoresPatchedCheckpoint() = runBlocking {
        val (files, storyId) = createStory()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val round1SyncStarted = CompletableDeferred<Unit>()
        val releaseRound1Sync = CompletableDeferred<Unit>()
        val staleRound2Started = CompletableDeferred<Unit>()

        val client = FakeSessionLlmClient(
            streamHandler = { idx, _ ->
                val text = when (idx) {
                    0 -> prose("首轮旧剧情")
                    1 -> prose("次轮旧剧情")
                    else -> prose("次轮重写新剧情")
                }
                listOf(LlmStreamEvent.Delta(text), LlmStreamEvent.Finished(finishReason = "stop"))
            },
            completeHandler = { _, req ->
                val content = req.messages.last().content
                when {
                    content.contains("走到路口") -> {
                        round1SyncStarted.complete(Unit)
                        releaseRound1Sync.await()
                        """{"scene":{"location":"云桥里路口"},"summary":"第1轮来到路口"}"""
                    }
                    content.contains("原本打算去地铁站") -> {
                        staleRound2Started.complete(Unit)
                        kotlinx.coroutines.delay(5_000)
                        """{"scene":{"location":"应被取消的地点"},"summary":"不应写入"}"""
                    }
                    content.contains("重写") -> {
                        """{"scene":{"location":"天台花园"},"summary":"第2轮改去天台花园"}"""
                    }
                    else -> """{"summary":"默认摘要"}"""
                }
            }
        )

        val (repo, session) = newSession(files, storyId, scope, client, awaitPreviousSyncTimeoutMs = 40L)
        try {
            // 启动第 1 轮并卡住第 1 轮后台同步
            session.startRound(PlayerInput("走到路口"))
            withTimeout(5_000) {
                session.roundEvents.first { it is RoundEvent.NarrativeCommitted && it.turn == 1 }
            }
            withTimeout(5_000) { round1SyncStarted.await() }

            // 第 2 轮建立 provisional=true 检查点，并入队第 2 轮同步（位于队列中等待第 1 轮完成）
            session.startRound(PlayerInput("原本打算去地铁站"))
            withTimeout(5_000) {
                session.roundEvents.first { it is RoundEvent.NarrativeCommitted && it.turn == 2 }
            }

            // 第 1 轮仍在同步，同时第 2 轮在队列里等待；触发从第 2 轮重新生成（R9）
            var restoredFired = false
            session.regenerate(
                turn = 2,
                revisedInput = PlayerInput("改去天台花园重写"),
                onRestored = { restoredFired = true }
            )
            withTimeout(5_000) {
                session.state.first { it.statusText.contains("恢复第2轮") }
            }
            // 等一小会儿让 cancelFrom(2) 持锁并清理 queued round 2，再放行 round 1
            kotlinx.coroutines.delay(30)
            releaseRound1Sync.complete(Unit)

            withTimeout(5_000) {
                session.roundEvents.first { it is RoundEvent.NarrativeCommitted && it.turn == 2 }
            }
            assertTrue(withTimeout(5_000) { session.awaitSyncIdle(5_000) })

            assertTrue(restoredFired)
            assertFalse(staleRound2Started.isCompleted)
            val snap = repo.snapshot(storyId)
            assertEquals("天台花园", snap.story.currentLocation)
            assertTrue(snap.narratives.last().narrative.startsWith("次轮重写新剧情"))
            // 第 2 轮的检查点中已记录第 1 轮完成后的“云桥里路口”
            assertEquals("云桥里路口", files.loadRoundCheckpoint(storyId, 2)!!.storyState.currentLocation)
        } finally {
            session.close()
            scope.cancel()
        }
    }

    @Test
    fun r10_openAutoCatchupsLegacyPendingFlagAndClearsFlagAfterSuccess() = runBlocking {
        val legacyFlags = mapOf(StateSyncPolicy.PENDING_FROM_ROUND_KEY to "2")
        val existing = listOf(
            NarrativeEntry(turn = 1, userInput = "醒来", narrative = prose("第一轮")),
            NarrativeEntry(turn = 2, userInput = "出门", narrative = prose("第二轮")),
            NarrativeEntry(turn = 3, userInput = "到达公园", narrative = prose("第三轮"))
        )
        val (files, storyId) = createStory(
            stateFlags = legacyFlags,
            existingNarratives = existing,
            currentRound = 3
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        val client = FakeSessionLlmClient(
            streamHandler = { _, _ -> emptyList() },
            completeHandler = { _, _ ->
                """{"scene":{"location":"海临中央公园"},"summary":"补齐第2至3轮状态同步"}"""
            }
        )

        val (repo, session) = newSession(files, storyId, scope, client)
        try {
            session.open()
            assertTrue(withTimeout(5_000) { session.awaitSyncIdle(5_000) })
            withTimeout(5_000) {
                session.state.first { it.sync.lastSyncedRound == 3 && it.sync.syncingRounds.isEmpty() }
            }

            val snap = repo.snapshot(storyId)
            assertNull(StateSyncPolicy.pendingFromRound(snap.story))
            assertEquals("海临中央公园", snap.story.currentLocation)
            assertTrue(snap.summaries.any { it.turn == 3 })
        } finally {
            session.close()
            scope.cancel()
        }
    }

    @Test
    fun cancelNarrative_stopsActiveStreamWithoutCancelingQueuedSync() = runBlocking {
        val (files, storyId) = createStory()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val syncRound1Started = CompletableDeferred<Unit>()
        val releaseSyncRound1 = CompletableDeferred<Unit>()
        val round2StreamStarted = CompletableDeferred<Unit>()
        val releaseRound2Stream = CompletableDeferred<Unit>()

        val client = object : LlmClient {
            var streamCount = 0
            override fun stream(request: LlmRequest): Flow<LlmStreamEvent> = flow {
                val index = streamCount++
                if (index == 0) {
                    val text = prose("第一轮完整正文")
                    emit(LlmStreamEvent.Delta(text))
                    emit(LlmStreamEvent.Finished(finishReason = "stop"))
                } else {
                    emit(LlmStreamEvent.Delta("第二轮开端尚未完成，正在等待取消"))
                    round2StreamStarted.complete(Unit)
                    releaseRound2Stream.await()
                }
            }

            override suspend fun complete(request: LlmRequest): LlmCompletion {
                syncRound1Started.complete(Unit)
                releaseSyncRound1.await()
                return LlmCompletion("""{"scene":{"location":"咖啡馆"},"summary":"第1轮成功抵达咖啡馆"}""")
            }
        }

        val (repo, session) = newSession(files, storyId, scope, client, awaitPreviousSyncTimeoutMs = 40L)
        try {
            session.startRound(PlayerInput("出发前往咖啡馆"))
            withTimeout(5_000) {
                session.roundEvents.first { it is RoundEvent.NarrativeCommitted && it.turn == 1 }
            }
            withTimeout(5_000) { syncRound1Started.await() }

            // 第 2 轮开始流式生成并中途取消
            session.startRound(PlayerInput("点单"))
            withTimeout(5_000) { round2StreamStarted.await() }
            session.cancelNarrative()
            assertEquals(RoundPhase.IDLE, session.state.value.phase)

            // 放行第 1 轮同步，第 1 轮状态必须完整落盘
            releaseSyncRound1.complete(Unit)
            assertTrue(withTimeout(5_000) { session.awaitSyncIdle(5_000) })

            val snap = repo.snapshot(storyId)
            assertEquals(1, snap.narratives.size)
            assertEquals("咖啡馆", snap.story.currentLocation)
            assertEquals(1, session.state.value.sync.lastSyncedRound)
        } finally {
            releaseRound2Stream.cancel()
            session.close()
            scope.cancel()
        }
    }
}
