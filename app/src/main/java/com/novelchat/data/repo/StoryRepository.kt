package com.novelchat.data.repo

import com.google.gson.Gson
import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.CharacterIdentityPolicy
import com.novelchat.data.model.CharacterNamingPolicy
import com.novelchat.data.model.NarrationSettings
import com.novelchat.data.model.NarrativeEntry
import com.novelchat.data.model.PlayerState
import com.novelchat.data.model.RoundCheckpoint
import com.novelchat.data.model.StoryState
import com.novelchat.data.model.SummaryEntry
import com.novelchat.data.model.WorldbookEntry
import com.novelchat.data.storage.FileStorageManager
import com.novelchat.domain.engine.CharacterProfilePolicy
import com.novelchat.domain.engine.StateSyncPolicy
import com.novelchat.domain.v2.memory.ChapterSummary
import com.novelchat.domain.v2.state.ApplyResult
import com.novelchat.domain.v2.state.SyncTask
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class StoryRepository(
    private val files: FileStorageManager
) {
    private val gson = Gson()
    private val locks = ConcurrentHashMap<Long, Mutex>()
    private val snapshots = ConcurrentHashMap<Long, StorySnapshot>()
    private val flows = ConcurrentHashMap<Long, MutableStateFlow<StorySnapshot?>>()

    fun writeLock(storyId: Long): Mutex = locks.computeIfAbsent(storyId) { Mutex() }

    fun snapshotFlow(storyId: Long): StateFlow<StorySnapshot?> =
        flows.computeIfAbsent(storyId) { MutableStateFlow(snapshots[storyId]) }.asStateFlow()

    suspend fun snapshot(storyId: Long): StorySnapshot {
        snapshots[storyId]?.let { return it }
        return writeLock(storyId).withLock {
            snapshots[storyId] ?: loadFromDiskLocked(storyId).also { loaded ->
                publishLocked(storyId, loaded)
            }
        }
    }

    suspend fun saveCheckpoint(
        storyId: Long,
        round: Int,
        provisional: Boolean
    ): Boolean = withContext(NonCancellable) {
        writeLock(storyId).withLock {
            val snap = snapshots[storyId] ?: loadFromDiskLocked(storyId)
            val allWorldbook = files.loadAllWorldbookEntries(storyId)
            val checkpoint = RoundCheckpoint(
                version = RoundCheckpoint.CURRENT_VERSION,
                round = round,
                storyState = snap.story,
                playerState = snap.player,
                characterCards = snap.characters,
                worldbookEntries = allWorldbook,
                provisional = provisional,
                createdAt = files.getCurrentTimeString()
            )
            val created = files.saveRoundCheckpointIfAbsent(storyId, checkpoint)
            if (created) {
                publishLocked(
                    storyId,
                    snap.copy(
                        regeneratableTurns = snap.regeneratableTurns + round,
                        version = snap.version + 1
                    )
                )
            }
            created
        }
    }

    suspend fun patchCheckpointIfProvisional(storyId: Long, round: Int) = withContext(NonCancellable) {
        writeLock(storyId).withLock {
            if (round <= 0) return@withLock
            val existing = files.loadRoundCheckpoint(storyId, round) ?: return@withLock
            if (!existing.provisional) return@withLock
            val snap = snapshots[storyId] ?: loadFromDiskLocked(storyId)
            val preRoundStory = snap.story.copy(
                currentRound = minOf(snap.story.currentRound, round - 1)
            )
            val updated = RoundCheckpoint(
                version = RoundCheckpoint.CURRENT_VERSION,
                round = round,
                storyState = preRoundStory,
                playerState = snap.player,
                characterCards = snap.characters,
                worldbookEntries = files.loadAllWorldbookEntries(storyId),
                provisional = false,
                createdAt = existing.createdAt.ifBlank { files.getCurrentTimeString() }
            )
            files.saveRoundCheckpointForce(storyId, updated)
        }
    }

    suspend fun commitNarrative(storyId: Long, entry: NarrativeEntry): StorySnapshot =
        withContext(NonCancellable) {
            writeLock(storyId).withLock {
                val snap = snapshots[storyId] ?: loadFromDiskLocked(storyId)
                files.saveNarrative(storyId, entry)
                val nextRound = maxOf(snap.story.currentRound, entry.turn)
                val updatedStory = if (nextRound != snap.story.currentRound) {
                    snap.story.copy(currentRound = nextRound).also {
                        files.saveStoryState(storyId, it)
                    }
                } else {
                    snap.story
                }
                val updatedNarratives = (snap.narratives.filterNot { it.turn == entry.turn } + entry)
                    .sortedBy { it.turn }
                snap.copy(
                    story = updatedStory,
                    narratives = updatedNarratives,
                    version = snap.version + 1
                ).also { publishLocked(storyId, it) }
            }
        }

    suspend fun commit(
        storyId: Long,
        task: SyncTask,
        result: ApplyResult,
        chapter: ChapterSummary? = null,
        overview: ChapterSummary? = null
    ): StorySnapshot = withContext(NonCancellable) {
        writeLock(storyId).withLock {
            val currentSnap = snapshots[storyId] ?: loadFromDiskLocked(storyId)
            val allWrites = buildList {
                addAll(result.writes)
                chapter?.let {
                    add(EntityWrite(EntityWrite.TYPE_CHAPTER, it.index.toString(), gson.toJsonTree(it)))
                }
                overview?.let {
                    add(EntityWrite(EntityWrite.TYPE_CHAPTER, "overview", gson.toJsonTree(it)))
                }
            }

            if (task.lastRound > 0 && allWrites.isNotEmpty()) {
                val preparedJournal = RoundCommitJournal(
                    rounds = task.rounds,
                    status = RoundCommitJournal.STATUS_PREPARED,
                    writes = allWrites
                )
                files.saveCommitJournal(storyId, preparedJournal)
                applyJournalWritesLocked(storyId, allWrites)
                val nextSyncStatus = updateSyncStatusAfterCommitLocked(
                    storyId = storyId,
                    currentStatus = currentSnap.syncStatus,
                    rounds = task.rounds
                )
                files.saveCommitJournal(
                    storyId,
                    preparedJournal.copy(status = RoundCommitJournal.STATUS_COMPLETED)
                )
                files.deleteCommitJournal(storyId, preparedJournal.lastRound)

                val updatedChapters = mergeChapters(currentSnap.chapters, listOfNotNull(overview, chapter))
                val updatedSummaries = if (result.summary != null) {
                    (result.snapshot.summaries.filterNot { it.turn == result.summary.turn } + result.summary)
                        .sortedBy { it.turn }
                } else {
                    result.snapshot.summaries
                }
                return@withLock result.snapshot.copy(
                    chapters = updatedChapters,
                    summaries = updatedSummaries,
                    syncStatus = nextSyncStatus,
                    regeneratableTurns = currentSnap.regeneratableTurns,
                    version = currentSnap.version + 1
                ).also { publishLocked(storyId, it) }
            }

            val nextSyncStatus = if (task.lastRound > 0) {
                updateSyncStatusAfterCommitLocked(
                    storyId = storyId,
                    currentStatus = currentSnap.syncStatus,
                    rounds = task.rounds
                )
            } else {
                currentSnap.syncStatus
            }
            return@withLock result.snapshot.copy(
                syncStatus = nextSyncStatus,
                regeneratableTurns = currentSnap.regeneratableTurns,
                version = currentSnap.version + 1
            ).also { publishLocked(storyId, it) }
        }
    }

    suspend fun recordSyncFailure(
        storyId: Long,
        failedRounds: List<Int>,
        errorMessage: String
    ): SyncStatus = withContext(NonCancellable) {
        writeLock(storyId).withLock {
            val snap = snapshots[storyId] ?: loadFromDiskLocked(storyId)
            val prev = snap.syncStatus
            val updated = prev.copy(
                failedRounds = (prev.failedRounds + failedRounds).filter { it > 0 }.distinct().sorted(),
                lastError = errorMessage.trim().take(240),
                updatedAt = files.getCurrentTimeString()
            )
            files.saveSyncStatus(storyId, updated)
            publishLocked(
                storyId,
                snap.copy(syncStatus = updated, version = snap.version + 1)
            )
            updated
        }
    }

    suspend fun recoverPendingCommits(storyId: Long): StorySnapshot = withContext(NonCancellable) {
        writeLock(storyId).withLock {
            val journals = files.loadCommitJournals(storyId)
            var currentStatus = files.loadSyncStatus(storyId)
            journals.forEach { journal ->
                when (journal.status) {
                    RoundCommitJournal.STATUS_PREPARED -> {
                        applyJournalWritesLocked(storyId, journal.writes)
                        currentStatus = updateSyncStatusAfterCommitLocked(
                            storyId = storyId,
                            currentStatus = currentStatus,
                            rounds = journal.rounds
                        )
                        files.saveCommitJournal(
                            storyId,
                            journal.copy(status = RoundCommitJournal.STATUS_COMPLETED)
                        )
                        files.deleteCommitJournal(storyId, journal.lastRound)
                    }
                    RoundCommitJournal.STATUS_COMPLETED -> {
                        files.deleteCommitJournal(storyId, journal.lastRound)
                    }
                }
            }
            loadFromDiskLocked(storyId).also { publishLocked(storyId, it) }
        }
    }

    suspend fun restoreCheckpoint(storyId: Long, round: Int): StorySnapshot = withContext(NonCancellable) {
        writeLock(storyId).withLock {
            files.restoreRoundCheckpoint(storyId, round)
            files.deleteCommitJournalsFromRound(storyId, round)
            files.deleteChapterSummariesFromRound(storyId, round)
            val prevStatus = files.loadSyncStatus(storyId)
            val updatedStatus = prevStatus.copy(
                lastSyncedRound = minOf(prevStatus.lastSyncedRound, (round - 1).coerceAtLeast(0)),
                failedRounds = prevStatus.failedRounds.filter { it < round },
                lastError = "",
                updatedAt = files.getCurrentTimeString()
            )
            files.saveSyncStatus(storyId, updatedStatus)
            loadFromDiskLocked(storyId).also { publishLocked(storyId, it) }
        }
    }

    suspend fun updateNarrationSettings(storyId: Long, settings: NarrationSettings): StorySnapshot =
        edit(storyId) { storage ->
            storage.saveNarrationSettings(storyId, settings)
        }.let { snapshot(storyId) }

    suspend fun <T> edit(storyId: Long, block: suspend (FileStorageManager) -> T): T =
        writeLock(storyId).withLock {
            val result = block(files)
            if (snapshots.containsKey(storyId) || flows.containsKey(storyId)) {
                runCatching {
                    if (files.isInitialized(storyId)) {
                        loadFromDiskLocked(storyId).also { publishLocked(storyId, it) }
                    }
                }
            }
            result
        }

    fun invalidate(storyId: Long) {
        snapshots.remove(storyId)
        flows[storyId]?.value = null
    }

    private suspend fun loadFromDiskLocked(storyId: Long): StorySnapshot {
        check(files.isInitialized(storyId)) {
            "故事存档不存在或尚未完成初始化"
        }
        val world = files.loadWorldSetting(storyId)
        var story = files.loadStoryState(storyId)
        val storedPlayer = files.loadPlayerState(storyId)
        val player = storedPlayer ?: PlayerState(name = CharacterNamingPolicy.DEFAULT_PLAYER_NAME).also {
            files.savePlayerState(storyId, it)
        }
        val narratives = files.loadAllNarratives(storyId)
        val recoveredStory = StateSyncPolicy.reconcilePersistedNarratives(story, narratives)
        if (recoveredStory != story) {
            story = recoveredStory
            files.saveStoryState(storyId, story)
        }

        val rawCharacters = files.loadAllCharacterCards(storyId)
        val identityResult = CharacterIdentityPolicy.reconcileRoster(rawCharacters)
        val identityCards = identityResult.cards
        rawCharacters.zip(identityCards).forEach { (stored, reconciled) ->
            if (stored != reconciled) files.saveCharacterCard(storyId, reconciled)
        }
        if (identityResult.aliases.isNotEmpty()) {
            val reconciledActive = story.activeCharacters
                .map { name -> identityResult.aliases[name] ?: name }
                .distinct()
            if (reconciledActive != story.activeCharacters) {
                story = story.copy(activeCharacters = reconciledActive)
                files.saveStoryState(storyId, story)
            }
        }
        val characters = identityCards.map { card ->
            CharacterProfilePolicy.reconcile(card, player.name)
        }
        identityCards.zip(characters).forEach { (stored, reconciled) ->
            if (stored != reconciled) files.saveCharacterCard(storyId, reconciled)
        }

        val worldbook = files.loadActiveWorldbookEntries(storyId)
        val summaries = files.loadAllSummaries(storyId)
        val chapters = files.loadChapterSummaries(storyId)
        val narration = files.loadNarrationSettings(storyId)
        val syncStatus = files.loadSyncStatus(storyId)
        val checkpoints = files.loadRoundCheckpointTurns(storyId)
        val prevVersion = snapshots[storyId]?.version ?: 0L

        return StorySnapshot(
            storyId = storyId,
            world = world,
            story = story,
            player = player,
            characters = characters,
            worldbook = worldbook,
            narratives = narratives,
            summaries = summaries,
            chapters = chapters,
            narration = narration,
            syncStatus = syncStatus,
            regeneratableTurns = checkpoints,
            version = prevVersion + 1
        )
    }

    private suspend fun applyJournalWritesLocked(storyId: Long, writes: List<EntityWrite>) {
        writes.forEach { write ->
            val after = write.after
            when (write.type) {
                EntityWrite.TYPE_PLAYER -> {
                    if (after != null && !after.isJsonNull) {
                        files.savePlayerState(storyId, gson.fromJson(after, PlayerState::class.java))
                    }
                }
                EntityWrite.TYPE_CHARACTER -> {
                    if (after == null || after.isJsonNull) {
                        files.deleteCharacterCard(storyId, write.key)
                    } else {
                        files.saveCharacterCard(storyId, gson.fromJson(after, CharacterCard::class.java))
                    }
                }
                EntityWrite.TYPE_WORLDBOOK -> {
                    if (after == null || after.isJsonNull) {
                        files.deleteWorldbookEntry(storyId, write.key)
                    } else {
                        files.saveWorldbookEntry(storyId, gson.fromJson(after, WorldbookEntry::class.java))
                    }
                }
                EntityWrite.TYPE_STORY -> {
                    if (after != null && !after.isJsonNull) {
                        files.saveStoryState(storyId, gson.fromJson(after, StoryState::class.java))
                    }
                }
                EntityWrite.TYPE_SUMMARY -> {
                    if (after != null && !after.isJsonNull) {
                        files.saveSummary(storyId, gson.fromJson(after, SummaryEntry::class.java))
                    }
                }
                EntityWrite.TYPE_CHAPTER -> {
                    if (after != null && !after.isJsonNull) {
                        files.saveChapterSummary(storyId, gson.fromJson(after, ChapterSummary::class.java))
                    }
                }
            }
        }
    }

    private suspend fun updateSyncStatusAfterCommitLocked(
        storyId: Long,
        currentStatus: SyncStatus,
        rounds: List<Int>
    ): SyncStatus {
        val last = rounds.maxOrNull() ?: 0
        val updated = currentStatus.copy(
            lastSyncedRound = maxOf(currentStatus.lastSyncedRound, last),
            failedRounds = (currentStatus.failedRounds - rounds.toSet()).sorted(),
            lastError = "",
            updatedAt = files.getCurrentTimeString()
        )
        files.saveSyncStatus(storyId, updated)
        return updated
    }

    private fun mergeChapters(
        existing: List<ChapterSummary>,
        added: List<ChapterSummary>
    ): List<ChapterSummary> {
        if (added.isEmpty()) return existing
        val map = existing.associateBy { it.isOverview to it.index }.toMutableMap()
        added.forEach { map[it.isOverview to it.index] = it }
        return map.values.sortedWith(compareBy<ChapterSummary> { !it.isOverview }.thenBy { it.index })
    }

    private fun publishLocked(storyId: Long, snapshot: StorySnapshot) {
        snapshots[storyId] = snapshot
        flows.computeIfAbsent(storyId) { MutableStateFlow(snapshot) }.value = snapshot
    }
}
