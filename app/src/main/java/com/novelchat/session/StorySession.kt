package com.novelchat.session

import com.novelchat.data.repo.StoryRepository
import com.novelchat.domain.engine.StateSyncPolicy
import com.novelchat.domain.v2.context.PlayerInput
import com.novelchat.domain.v2.memory.MemoryManager
import com.novelchat.domain.v2.round.RoundEvent
import com.novelchat.domain.v2.round.RoundOrchestrator
import com.novelchat.domain.v2.state.StateKeeperAgent
import com.novelchat.domain.v2.state.SyncTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class StorySession(
    val storyId: Long,
    private val scope: CoroutineScope,
    private val repo: StoryRepository,
    private val orchestrator: RoundOrchestrator,
    stateKeeper: StateKeeperAgent,
    memoryManager: MemoryManager,
    private val isLocalNarrative: suspend () -> Boolean,
    private val isLocalStateKeeper: suspend () -> Boolean,
    private val awaitPreviousSyncTimeoutMs: Long = DEFAULT_AWAIT_SYNC_TIMEOUT_MS,
    clockNow: () -> String = {
        java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
    }
) {
    private val _state = MutableStateFlow(SessionState(storyId = storyId))
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _roundEvents = MutableSharedFlow<RoundEvent>(extraBufferCapacity = 64)
    val roundEvents: SharedFlow<RoundEvent> = _roundEvents.asSharedFlow()

    private val openMutex = Mutex()
    private var opened = false
    private var narrativeJob: Job? = null
    private val syncingCollectorJob: Job

    private val syncQueue: SyncQueue = SyncQueue(
        storyId = storyId,
        scope = scope,
        repo = repo,
        stateKeeper = stateKeeper,
        memoryManager = memoryManager,
        isLocalStateKeeper = isLocalStateKeeper,
        clockNow = clockNow,
        onSyncFinished = { completion ->
            _state.update { cur ->
                cur.copy(
                    sync = cur.sync.copy(
                        syncingRounds = syncQueue.syncingRounds.value,
                        failedRounds = cur.sync.failedRounds - completion.rounds.toSet(),
                        lastSyncedRound = maxOf(cur.sync.lastSyncedRound, completion.rounds.maxOrNull() ?: 0),
                        lastSyncCompletedAtMs = completion.completedAtMs,
                        lastError = null
                    ),
                    suggestions = if (completion.suggestions.isNotEmpty()) {
                        completion.suggestions
                    } else {
                        cur.suggestions
                    },
                    lastApplyIssues = completion.issues
                )
            }
        },
        onSyncFailed = { rounds, message ->
            _state.update { cur ->
                cur.copy(
                    sync = cur.sync.copy(
                        syncingRounds = syncQueue.syncingRounds.value,
                        failedRounds = (cur.sync.failedRounds + rounds).sorted().toSet(),
                        lastError = message
                    )
                )
            }
        }
    )

    init {
        syncingCollectorJob = scope.launch {
            syncQueue.syncingRounds.collect { rounds ->
                _state.update { cur ->
                    cur.copy(sync = cur.sync.copy(syncingRounds = rounds))
                }
            }
        }
    }

    suspend fun open() {
        openMutex.withLock {
            if (opened) return
            val recovered = repo.recoverPendingCommits(storyId)
            val syncStatus = recovered.syncStatus
            _state.update { cur ->
                cur.copy(
                    sync = cur.sync.copy(
                        failedRounds = syncStatus.failedRounds.toSet(),
                        lastSyncedRound = syncStatus.lastSyncedRound,
                        lastError = syncStatus.lastError.takeIf { it.isNotBlank() }
                    )
                )
            }

            // R10：若旧 V1 存档存在待补同步标记，自动入队追补并在成功后清除该标记
            val legacyPendingFrom = StateSyncPolicy.pendingFromRound(recovered.story)
            if (legacyPendingFrom != null) {
                val maxTurn = maxOf(
                    recovered.story.currentRound,
                    recovered.narratives.maxOfOrNull { it.turn } ?: 0
                )
                val pendingRounds = (legacyPendingFrom..maxTurn).toList().takeLast(MAX_CATCHUP_ROUNDS)
                if (pendingRounds.isNotEmpty()) {
                    syncQueue.enqueue(
                        SyncTask(
                            storyId = storyId,
                            rounds = pendingRounds,
                            clearsLegacyPendingFlag = true
                        )
                    )
                }
            }
            opened = true
        }
    }

    fun startRound(
        input: PlayerInput,
        onNarrativeCommitted: (suspend (Int, String) -> Unit)? = null
    ) {
        if (_state.value.phase != RoundPhase.IDLE && _state.value.phase != RoundPhase.FAILED) return
        narrativeJob?.cancel()
        narrativeJob = scope.launch {
            open()
            executeRoundFlow(
                targetTurn = null,
                input = input,
                prepareBeforeNarrative = {
                    val preSnap = repo.snapshot(storyId)
                    val nextTurn = maxOf(
                        preSnap.story.currentRound,
                        preSnap.narratives.maxOfOrNull { it.turn } ?: 0
                    ) + 1
                    _state.update { it.copy(activeTurn = nextTurn) }
                    val idle = syncQueue.awaitIdle(awaitPreviousSyncTimeoutMs)
                    val snap = if (idle) preSnap else repo.snapshot(storyId)
                    val resolvedTurn = maxOf(
                        snap.story.currentRound,
                        snap.narratives.maxOfOrNull { it.turn } ?: 0
                    ) + 1
                    _state.update { it.copy(activeTurn = resolvedTurn) }
                    repo.saveCheckpoint(storyId, resolvedTurn, provisional = !idle)
                    resolvedTurn
                },
                onNarrativeCommitted = onNarrativeCommitted
            )
        }
    }

    fun regenerate(
        turn: Int,
        revisedInput: PlayerInput,
        onRestored: (suspend () -> Unit)? = null,
        onNarrativeCommitted: (suspend (Int, String) -> Unit)? = null
    ) {
        val currentJob = narrativeJob
        narrativeJob = scope.launch {
            open()
            currentJob?.cancelAndJoin()
            _state.update {
                it.copy(
                    phase = RoundPhase.PREPARING,
                    statusText = "正在恢复第${turn}轮开始前的状态...",
                    canCancelGeneration = false,
                    streamingText = "",
                    activeTurn = turn,
                    activeUserInput = revisedInput.raw,
                    errorMessage = null,
                    lastFailedAction = null
                )
            }
            syncQueue.cancelFrom(turn)
            val restored = repo.restoreCheckpoint(storyId, turn)
            _state.update { cur ->
                cur.copy(
                    sync = SyncState(
                        syncingRounds = syncQueue.syncingRounds.value,
                        failedRounds = restored.syncStatus.failedRounds.toSet(),
                        lastSyncedRound = restored.syncStatus.lastSyncedRound,
                        lastError = restored.syncStatus.lastError.takeIf { it.isNotBlank() }
                    ),
                    suggestions = emptyList()
                )
            }
            onRestored?.invoke()

            executeRoundFlow(
                targetTurn = turn,
                input = revisedInput,
                prepareBeforeNarrative = { turn },
                onNarrativeCommitted = onNarrativeCommitted
            )
        }
    }

    fun cancelNarrative() {
        val job = narrativeJob ?: return
        if (!job.isActive) return
        val phase = _state.value.phase
        if (phase == RoundPhase.COMMITTING) return
        job.cancel()
        _state.update { cur ->
            cur.copy(
                phase = RoundPhase.IDLE,
                statusText = "",
                canCancelGeneration = false,
                streamingText = "",
                activeUserInput = ""
            )
        }
    }

    fun retrySync() {
        scope.launch {
            open()
            val snap = repo.snapshot(storyId)
            val failed = _state.value.sync.failedRounds
            val roundsToSync = if (failed.isNotEmpty()) {
                failed.sorted()
            } else {
                val maxTurn = maxOf(
                    snap.story.currentRound,
                    snap.narratives.maxOfOrNull { it.turn } ?: 0
                )
                val lastSynced = _state.value.sync.lastSyncedRound
                if (maxTurn > lastSynced) ((lastSynced + 1)..maxTurn).toList() else emptyList()
            }
            if (roundsToSync.isEmpty()) return@launch
            roundsToSync.chunked(MAX_CATCHUP_ROUNDS).forEach { batch ->
                syncQueue.enqueue(
                    SyncTask(
                        storyId = storyId,
                        rounds = batch,
                        clearsLegacyPendingFlag = StateSyncPolicy.pendingFromRound(snap.story) != null
                    )
                )
            }
        }
    }

    fun clearError() {
        _state.update { cur ->
            cur.copy(
                errorMessage = null,
                phase = if (cur.phase == RoundPhase.FAILED) RoundPhase.IDLE else cur.phase
            )
        }
    }

    suspend fun awaitSyncIdle(timeoutMs: Long = 30_000L): Boolean = syncQueue.awaitIdle(timeoutMs)

    suspend fun close() {
        narrativeJob?.cancel()
        syncingCollectorJob.cancel()
        syncQueue.close()
        repo.invalidate(storyId)
    }

    private suspend fun executeRoundFlow(
        targetTurn: Int?,
        input: PlayerInput,
        prepareBeforeNarrative: suspend () -> Int,
        onNarrativeCommitted: (suspend (Int, String) -> Unit)?
    ) {
        try {
            _state.update {
                it.copy(
                    phase = RoundPhase.PREPARING,
                    statusText = "正在整理上一轮...",
                    canCancelGeneration = true,
                    streamingText = "",
                    activeTurn = targetTurn ?: 0,
                    activeUserInput = input.raw,
                    errorMessage = null,
                    lastFailedAction = null
                )
            }
            val turn = prepareBeforeNarrative()
            _state.update { it.copy(activeTurn = turn) }
            val isLocal = isLocalNarrative()

            orchestrator.run(
                storyId = storyId,
                turn = turn,
                input = input,
                isLocalEndpoint = isLocal
            ).collect { event ->
                when (event) {
                    is RoundEvent.Loading -> {
                        val nextPhase = when {
                            event.message.contains("保存") -> RoundPhase.COMMITTING
                            _state.value.streamingText.isNotEmpty() -> RoundPhase.GUARDING
                            else -> _state.value.phase
                        }
                        _state.update { cur ->
                            cur.copy(
                                phase = nextPhase,
                                statusText = event.message,
                                canCancelGeneration = nextPhase != RoundPhase.COMMITTING
                            )
                        }
                    }
                    is RoundEvent.NarrativeDelta -> {
                        _state.update { cur ->
                            cur.copy(
                                phase = RoundPhase.NARRATING,
                                streamingText = cur.streamingText + event.text,
                                canCancelGeneration = true
                            )
                        }
                    }
                    is RoundEvent.NarrativeReset -> {
                        _state.update { cur ->
                            cur.copy(
                                phase = RoundPhase.NARRATING,
                                streamingText = "",
                                canCancelGeneration = true
                            )
                        }
                    }
                    is RoundEvent.NarrativeCommitted -> {
                        onNarrativeCommitted?.invoke(event.turn, event.narrative)
                        // R8：自动携带前序失败轮次一起追补（最多 3 轮）
                        val catchupRounds = (_state.value.sync.failedRounds + event.turn)
                            .distinct()
                            .sorted()
                            .takeLast(MAX_CATCHUP_ROUNDS)
                        syncQueue.enqueue(SyncTask(storyId = storyId, rounds = catchupRounds))
                        _state.update { cur ->
                            cur.copy(
                                phase = RoundPhase.IDLE,
                                statusText = "",
                                canCancelGeneration = false,
                                streamingText = "",
                                activeUserInput = "",
                                errorMessage = null,
                                lastFailedAction = null,
                                lastDebugInfo = event.debugInfo
                            )
                        }
                    }
                    is RoundEvent.Failed -> {
                        _state.update { cur ->
                            cur.copy(
                                phase = RoundPhase.FAILED,
                                statusText = "",
                                canCancelGeneration = false,
                                streamingText = "",
                                activeUserInput = "",
                                errorMessage = event.message,
                                lastFailedAction = input.raw
                            )
                        }
                    }
                }
                _roundEvents.tryEmit(event)
            }
        } catch (e: CancellationException) {
            _state.update { cur ->
                cur.copy(
                    phase = RoundPhase.IDLE,
                    statusText = "",
                    canCancelGeneration = false,
                    streamingText = "",
                    activeUserInput = "",
                    lastFailedAction = input.raw
                )
            }
            throw e
        } catch (e: Exception) {
            _state.update { cur ->
                cur.copy(
                    phase = RoundPhase.FAILED,
                    statusText = "",
                    canCancelGeneration = false,
                    streamingText = "",
                    activeUserInput = "",
                    errorMessage = e.message ?: "生成失败",
                    lastFailedAction = input.raw
                )
            }
        }
    }

    companion object {
        const val DEFAULT_AWAIT_SYNC_TIMEOUT_MS = 20_000L
        const val MAX_CATCHUP_ROUNDS = 3
    }
}
