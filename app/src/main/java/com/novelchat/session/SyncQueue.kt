package com.novelchat.session

import com.novelchat.data.repo.StoryRepository
import com.novelchat.domain.v2.memory.MemoryManager
import com.novelchat.domain.v2.state.ApplyIssue
import com.novelchat.domain.v2.state.StateDeltaApplier
import com.novelchat.domain.v2.state.StateKeeperAgent
import com.novelchat.domain.v2.state.SyncTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class SyncCompletionEvent(
    val rounds: List<Int>,
    val suggestions: List<String>,
    val issues: List<ApplyIssue>,
    val completedAtMs: Long
)

class SyncQueue(
    private val storyId: Long,
    private val scope: CoroutineScope,
    private val repo: StoryRepository,
    private val stateKeeper: StateKeeperAgent,
    private val memoryManager: MemoryManager,
    private val isLocalStateKeeper: suspend () -> Boolean,
    private val clockNow: () -> String = {
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
    },
    private val onSyncFinished: (SyncCompletionEvent) -> Unit = {},
    private val onSyncFailed: (List<Int>, String) -> Unit = { _, _ -> }
) {
    private val queueMutex = Mutex()
    private val pending = ArrayDeque<SyncTask>()
    private val wakeup = Channel<Unit>(Channel.CONFLATED)
    private val idleSignal = MutableStateFlow(true)
    private val _syncingRounds = MutableStateFlow<Set<Int>>(emptySet())
    val syncingRounds: StateFlow<Set<Int>> = _syncingRounds.asStateFlow()

    private var activeTask: SyncTask? = null
    private var activeJob: Job? = null
    private var isCommitting = false
    private var closed = false

    private val workerJob: Job = scope.launch {
        for (signal in wakeup) {
            while (true) {
                val nextTask = queueMutex.withLock {
                    if (closed || pending.isEmpty()) {
                        activeTask = null
                        activeJob = null
                        updateSyncingRoundsLocked()
                        idleSignal.value = true
                        null
                    } else {
                        val task = pending.removeFirst()
                        activeTask = task
                        isCommitting = false
                        updateSyncingRoundsLocked()
                        idleSignal.value = false
                        task
                    }
                } ?: break

                val taskJob = scope.launch {
                    executeTask(nextTask)
                }
                queueMutex.withLock {
                    activeJob = taskJob
                }
                taskJob.join()

                queueMutex.withLock {
                    activeTask = null
                    activeJob = null
                    isCommitting = false
                    updateSyncingRoundsLocked()
                    if (pending.isEmpty()) {
                        idleSignal.value = true
                    }
                }
            }
        }
    }

    suspend fun enqueue(task: SyncTask) {
        if (task.rounds.isEmpty()) return
        queueMutex.withLock {
            if (closed) return
            val existingRounds = (activeTask?.rounds.orEmpty() + pending.flatMap { it.rounds }).toSet()
            val newRounds = task.rounds.filterNot { it in existingRounds }
            val effectiveRounds = if (newRounds.isNotEmpty()) {
                (task.rounds).distinct().sorted()
            } else if (task.clearsLegacyPendingFlag) {
                task.rounds.distinct().sorted()
            } else {
                emptyList()
            }
            if (effectiveRounds.isEmpty()) return@withLock

            pending.addLast(task.copy(rounds = effectiveRounds))
            updateSyncingRoundsLocked()
            idleSignal.value = false
            wakeup.trySend(Unit)
        }
    }

    suspend fun awaitIdle(timeoutMs: Long): Boolean {
        if (idleSignal.value) return true
        return withTimeoutOrNull(timeoutMs) {
            idleSignal.first { it }
        } != null
    }

    /**
     * Rule R9: 取消待执行及未提交的 round >= fromTurn 任务，并等待所有 round < fromTurn 任务完成。
     */
    suspend fun cancelFrom(fromTurn: Int) {
        val jobToAwait = queueMutex.withLock {
            val retained = pending.filter { it.lastRound < fromTurn }
            pending.clear()
            retained.forEach { pending.addLast(it) }

            val current = activeTask
            val currentJob = activeJob
            if (current != null && current.lastRound >= fromTurn && !isCommitting) {
                currentJob?.cancel()
            }
            updateSyncingRoundsLocked()
            if (current == null && pending.isEmpty()) {
                idleSignal.value = true
                null
            } else {
                currentJob
            }
        }
        jobToAwait?.join()
        idleSignal.first { it }
    }

    suspend fun close() {
        val jobToCancel = queueMutex.withLock {
            closed = true
            pending.clear()
            wakeup.close()
            updateSyncingRoundsLocked()
            activeJob
        }
        jobToCancel?.cancel()
        workerJob.cancel()
    }

    private suspend fun executeTask(task: SyncTask) {
        try {
            coroutineScope {
                val base = repo.snapshot(task.storyId)
                val isLocal = isLocalStateKeeper()
                val deltaDeferred = async { stateKeeper.request(base, task, isLocal) }
                val chapterDeferred = if (memoryManager.chapterDue(base, task.lastRound)) {
                    async { memoryManager.summarizeChapterIfDue(base, task.lastRound, clockNow()) }
                } else {
                    null
                }

                val delta = deltaDeferred.await()
                val chapterBundle = chapterDeferred?.let {
                    try {
                        it.await()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                }

                // 重新读取最新快照，保留后台请求期间用户在详情页或世界书的手动编辑
                val latest = repo.snapshot(task.storyId)
                val applyResult = StateDeltaApplier.apply(
                    base = latest,
                    delta = delta,
                    rounds = task.rounds,
                    now = clockNow(),
                    clearLegacyPendingFlag = task.clearsLegacyPendingFlag
                )

                queueMutex.withLock { isCommitting = true }
                try {
                    repo.commit(
                        storyId = task.storyId,
                        task = task,
                        result = applyResult,
                        chapter = chapterBundle?.chapter,
                        overview = chapterBundle?.overview
                    )
                    repo.patchCheckpointIfProvisional(task.storyId, task.lastRound + 1)
                } finally {
                    queueMutex.withLock { isCommitting = false }
                }

                onSyncFinished(
                    SyncCompletionEvent(
                        rounds = task.rounds,
                        suggestions = applyResult.suggestions,
                        issues = applyResult.issues,
                        completedAtMs = System.currentTimeMillis()
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = e.message?.takeIf { it.isNotBlank() } ?: "状态同步失败"
            try {
                repo.recordSyncFailure(storyId, task.rounds, message)
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
            }
            onSyncFailed(task.rounds, message)
        }
    }

    private fun updateSyncingRoundsLocked() {
        val all = buildSet {
            activeTask?.rounds?.let(::addAll)
            pending.forEach { addAll(it.rounds) }
        }
        _syncingRounds.value = all
    }
}
