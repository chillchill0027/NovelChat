package com.novelchat.data.repo

import com.google.gson.Gson
import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.NarrativeEntry
import com.novelchat.data.model.PlayerState
import com.novelchat.data.model.RoundCheckpoint
import com.novelchat.data.model.StoryState
import com.novelchat.data.model.SummaryEntry
import com.novelchat.data.model.WorldSetting
import com.novelchat.data.model.WorldbookEntry
import com.novelchat.data.storage.FileStorageManager
import com.novelchat.domain.v2.memory.ChapterSummary
import com.novelchat.domain.v2.state.ApplyResult
import com.novelchat.domain.v2.state.SyncTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StoryRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val gson = Gson()

    private suspend fun createRepoAndStory(): Pair<StoryRepository, Long> {
        val files = FileStorageManager.forDirectory(tempFolder.root)
        val storyId = files.createConversationStorage()
        files.saveWorldSetting(storyId, WorldSetting(name = "海临市", description = "都市测试"))
        files.savePlayerState(storyId, PlayerState(name = "林克"))
        files.saveStoryState(storyId, StoryState(currentLocation = "云桥里"))
        files.completeConversationStorage(storyId)
        return StoryRepository(files) to storyId
    }

    @Test
    fun recoverPendingCommits_replaysPreparedJournalIdempotently() = runBlocking {
        val files = FileStorageManager.forDirectory(tempFolder.root)
        val (repo, storyId) = createRepoAndStory()
        val updatedPlayer = PlayerState(name = "林克", cv = 25)
        val updatedStory = StoryState(currentRound = 1, currentLocation = "便利店")
        val card = CharacterCard(id = "许棠", name = "许棠", occupation = "店员")

        val journal = RoundCommitJournal(
            rounds = listOf(1),
            status = RoundCommitJournal.STATUS_PREPARED,
            writes = listOf(
                EntityWrite(EntityWrite.TYPE_PLAYER, "player", gson.toJsonTree(updatedPlayer)),
                EntityWrite(EntityWrite.TYPE_STORY, "story", gson.toJsonTree(updatedStory)),
                EntityWrite(EntityWrite.TYPE_CHARACTER, "许棠", gson.toJsonTree(card))
            )
        )
        files.saveCommitJournal(storyId, journal)

        val recovered = repo.recoverPendingCommits(storyId)

        assertEquals(25, recovered.player.cv)
        assertEquals("便利店", recovered.story.currentLocation)
        assertTrue(recovered.characters.any { it.name == "许棠" })
        assertEquals(1, recovered.syncStatus.lastSyncedRound)
        assertTrue(files.loadCommitJournals(storyId).isEmpty())
    }

    @Test
    fun patchCheckpointIfProvisional_updatesAfterConcurrentNextNarrativeCommit() = runBlocking {
        val files = FileStorageManager.forDirectory(tempFolder.root)
        val (repo, storyId) = createRepoAndStory()

        // 第 1 轮正文先落盘
        repo.commitNarrative(storyId, NarrativeEntry(turn = 1, userInput = "出门", narrative = "出门了"))
        // 第 1 轮尚未同步完成时开启第 2 轮，建立 provisional checkpoint
        repo.saveCheckpoint(storyId, round = 2, provisional = true)
        assertTrue(files.loadRoundCheckpoint(storyId, 2)!!.provisional)

        // 第 2 轮正文也落盘了（currentRound 已推进到 2）
        repo.commitNarrative(storyId, NarrativeEntry(turn = 2, userInput = "买咖啡", narrative = "买了咖啡"))

        // 此时第 1 轮后台同步才完成，提交并修补第 2 轮的 provisional 检查点
        val base = repo.snapshot(storyId)
        val syncedStory = base.story.copy(currentLocation = "望潮路便利店")
        val result = ApplyResult(
            snapshot = base.copy(story = syncedStory),
            writes = listOf(EntityWrite(EntityWrite.TYPE_STORY, "story", gson.toJsonTree(syncedStory))),
            issues = emptyList(),
            summary = SummaryEntry(turn = 1, summary = "林克出门到便利店"),
            suggestions = emptyList()
        )
        repo.commit(storyId, SyncTask(storyId, listOf(1)), result)
        repo.patchCheckpointIfProvisional(storyId, 2)

        val patched = files.loadRoundCheckpoint(storyId, 2)!!
        assertFalse(patched.provisional)
        assertEquals(1, patched.storyState.currentRound)
        assertEquals("望潮路便利店", patched.storyState.currentLocation)
    }

    @Test
    fun restoreCheckpoint_cleansLaterChaptersAndSyncStatus() = runBlocking {
        val files = FileStorageManager.forDirectory(tempFolder.root)
        val (repo, storyId) = createRepoAndStory()

        repo.saveCheckpoint(storyId, round = 1, provisional = false)
        repo.commitNarrative(storyId, NarrativeEntry(turn = 1, userInput = "1", narrative = "n1"))
        repo.saveCheckpoint(storyId, round = 2, provisional = false)
        repo.commitNarrative(storyId, NarrativeEntry(turn = 2, userInput = "2", narrative = "n2"))
        val snap = repo.snapshot(storyId)
        val ch1 = ChapterSummary(index = 1, fromTurn = 1, toTurn = 1, text = "第一章")
        val ch2 = ChapterSummary(index = 2, fromTurn = 2, toTurn = 2, text = "第二章")
        repo.commit(
            storyId,
            SyncTask(storyId, listOf(2)),
            ApplyResult(snap, emptyList(), emptyList(), null, emptyList()),
            chapter = ch2
        )
        files.saveChapterSummary(storyId, ch1)
        repo.recordSyncFailure(storyId, listOf(2, 3), "err")

        val restored = repo.restoreCheckpoint(storyId, round = 2)

        assertEquals(1, restored.narratives.size)
        assertEquals(listOf(1), restored.chapters.map { it.index })
        assertEquals(1, restored.syncStatus.lastSyncedRound)
        assertTrue(restored.syncStatus.failedRounds.isEmpty())
    }

    @Test
    fun concurrentReadsCommitsAndEdits_remainConsistent() = runBlocking {
        val (repo, storyId) = createRepoAndStory()

        coroutineScope {
            repeat(12) { idx ->
                launch(Dispatchers.Default) {
                    val turn = idx + 1
                    repo.commitNarrative(
                        storyId,
                        NarrativeEntry(turn = turn, userInput = "u$turn", narrative = "n$turn")
                    )
                }
                launch(Dispatchers.Default) {
                    repo.edit(storyId) { storage ->
                        storage.saveWorldbookEntry(
                            storyId,
                            WorldbookEntry(id = "wb_$idx", type = "location", name = "地点$idx")
                        )
                    }
                }
                launch(Dispatchers.Default) {
                    assertNotNull(repo.snapshot(storyId).world)
                }
            }
        }

        val finalSnap = repo.snapshot(storyId)
        assertEquals(12, finalSnap.narratives.size)
        assertEquals(12, finalSnap.worldbook.size)
    }
}
