package com.novelchat.data.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novelchat.data.model.NarrativeEntry
import com.novelchat.data.model.PlayerState
import com.novelchat.data.model.StoryState
import com.novelchat.data.model.WorldSetting
import com.novelchat.data.model.WorldbookEntry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FileStorageManagerInstrumentedTest {
    @Test
    fun newlyReservedStoryIsNotVisibleUntilCreationCompletes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = FileStorageManager(context)
        val characterId = storage.createConversationStorage()

        try {
            assertFalse(storage.isInitialized(characterId))
            storage.saveWorldSetting(characterId, WorldSetting(name = "创建中测试"))
            storage.savePlayerState(characterId, PlayerState(name = "林克"))

            assertFalse(storage.isInitialized(characterId))
            assertFalse(storage.recoverConversationEntries().any { it.id == characterId })

            storage.completeConversationStorage(characterId)

            assertTrue(storage.isInitialized(characterId))
            assertTrue(storage.recoverConversationEntries().any { it.id == characterId })
        } finally {
            storage.deleteConversation(characterId)
        }
    }

    @Test
    fun storyStateRecoversAfterInterruptedReplacement() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = FileStorageManager(context)
        val characterId = 9_000_000_001L
        val storyState = StoryState(
            currentRound = 3,
            currentLocation = "test location",
            pendingEvents = listOf("test pending event")
        )

        storage.deleteConversation(characterId)
        try {
            storage.initialize(characterId)
            storage.saveStoryState(characterId, storyState)

            val stateFile = File(context.filesDir, "rpg/$characterId/states.json")
            val backupFile = File(stateFile.parentFile, stateFile.name + ".bak")
            assertTrue(stateFile.renameTo(backupFile))
            assertFalse(stateFile.exists())

            assertEquals(storyState, storage.loadStoryState(characterId))
            assertTrue(stateFile.exists())
            assertFalse(backupFile.exists())

            val narrative = NarrativeEntry(
                turn = 3,
                narrative = "test narrative",
                userInput = "test action",
                createdAt = "2026-07-13T20:00:00"
            )
            storage.saveNarrative(characterId, narrative)
            assertEquals(listOf(narrative), storage.loadAllNarratives(characterId))
        } finally {
            storage.deleteConversation(characterId)
        }
    }

    @Test
    fun changingWorldbookTypeRemovesPreviousCategoryCopy() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = FileStorageManager(context)
        val characterId = 9_000_000_002L

        storage.deleteConversation(characterId)
        try {
            storage.initialize(characterId)
            storage.saveWorldbookEntry(
                characterId,
                WorldbookEntry(
                    id = "same-entry",
                    type = "location",
                    name = "Old location",
                    lastModified = "2026-07-13T20:00:00"
                )
            )
            storage.saveWorldbookEntry(
                characterId,
                WorldbookEntry(
                    id = "same-entry",
                    type = "event",
                    name = "Updated event",
                    lastModified = "2026-07-13T20:01:00"
                )
            )

            val entries = storage.loadAllWorldbookEntries(characterId)
            assertEquals(1, entries.size)
            assertEquals("event", entries.single().type)
            assertFalse(
                File(context.filesDir, "rpg/$characterId/worldbook/locations/same-entry.json").exists()
            )
        } finally {
            storage.deleteConversation(characterId)
        }
    }

    @Test
    fun recoverConversationEntries_rebuildsLobbyEntryFromStoryDirectory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = FileStorageManager(context)
        val characterId = 9_000_000_003L

        storage.deleteConversation(characterId)
        try {
            storage.initialize(characterId)
            storage.saveWorldSetting(
                characterId,
                WorldSetting(name = "恢复测试世界", description = "测试存档恢复")
            )
            storage.savePlayerState(characterId, PlayerState(name = "许安"))
            storage.saveNarrative(
                characterId,
                NarrativeEntry(
                    turn = 2,
                    narrative = "许安回到了家。",
                    createdAt = "2026-07-18T12:00:00"
                )
            )

            val recovered = storage.recoverConversationEntries().single { it.id == characterId }

            assertEquals("许安", recovered.characterName)
            assertEquals(2, recovered.messageCount)
            assertEquals("许安回到了家。", recovered.lastMessage)
        } finally {
            storage.deleteConversation(characterId)
        }
    }

    @Test
    fun nonPositiveStoryIdsAreRejected() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = FileStorageManager(context)

        listOf(0L, -1L).forEach { invalidId ->
            var rejected = false
            try {
                storage.isInitialized(invalidId)
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            assertTrue("Expected story id $invalidId to be rejected", rejected)
        }
    }
}
