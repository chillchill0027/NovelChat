package com.novelchat.data.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.CharacterFieldDefinition
import com.novelchat.data.model.NarrativeEntry
import com.novelchat.data.model.PlayerState
import com.novelchat.data.model.StoryState
import com.novelchat.data.model.SummaryEntry
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
    fun customCharacterFieldsAndValues_surviveStorageRoundTrip() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = FileStorageManager(context)
        val characterId = 9_000_000_006L
        val field = CharacterFieldDefinition(
            key = "arcane_affinity",
            label = "奥术亲和",
            group = CharacterFieldDefinition.GROUP_ABILITY,
            valueType = CharacterFieldDefinition.TYPE_NUMBER,
            minValue = 0,
            maxValue = 10,
            defaultValue = "2",
            updateMode = CharacterFieldDefinition.UPDATE_EVENT
        )

        storage.deleteConversation(characterId)
        try {
            storage.initialize(characterId)
            storage.saveWorldSetting(
                characterId,
                WorldSetting(name = "星海学宫", characterFields = listOf(field))
            )
            storage.savePlayerState(
                characterId,
                PlayerState(name = "林克", customAttributes = mapOf("arcane_affinity" to "7"))
            )
            storage.saveCharacterCard(
                characterId,
                CharacterCard(
                    id = "wang_chang",
                    name = "王常",
                    customAttributes = mapOf("arcane_affinity" to "5")
                )
            )

            assertEquals("奥术亲和", storage.loadWorldSetting(characterId).characterFields.single().label)
            assertEquals("7", storage.loadPlayerState(characterId)?.customAttributes?.get("arcane_affinity"))
            assertEquals(
                "5",
                storage.loadCharacterCard(characterId, "wang_chang")?.customAttributes?.get("arcane_affinity")
            )
        } finally {
            storage.deleteConversation(characterId)
        }
    }

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
    fun roundCheckpoint_restoresStateAndTruncatesFutureBranch() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = FileStorageManager(context)
        val characterId = 9_000_000_004L
        val originalPlayer = PlayerState(
            name = "林克",
            inventory = listOf(com.novelchat.data.model.InventoryItem(name = "旧钥匙"))
        )
        val originalStory = StoryState(
            currentRound = 1,
            currentLocation = "旧公寓",
            activeCharacters = listOf("王常")
        )
        val originalCard = CharacterCard(
            id = "wang_chang",
            name = "王常",
            occupation = "邻居",
            mentalState = com.novelchat.data.model.MentalState(mood = "平静")
        )
        val originalWorldbook = WorldbookEntry(
            id = "old_home",
            type = "location",
            name = "旧公寓",
            description = "林克目前居住的地方"
        )

        storage.deleteConversation(characterId)
        try {
            storage.initialize(characterId)
            storage.saveWorldSetting(characterId, WorldSetting(name = "分支测试"))
            storage.savePlayerState(characterId, originalPlayer)
            storage.saveStoryState(characterId, originalStory)
            storage.saveCharacterCard(characterId, originalCard)
            storage.saveWorldbookEntry(characterId, originalWorldbook)
            storage.saveNarrative(
                characterId,
                NarrativeEntry(turn = 1, userInput = "回家", narrative = "林克回到旧公寓。")
            )
            storage.saveSummary(characterId, SummaryEntry(turn = 1, summary = "林克回家。"))

            assertTrue(storage.saveNextRoundCheckpointIfAbsent(characterId))

            storage.savePlayerState(
                characterId,
                originalPlayer.copy(
                    inventory = originalPlayer.inventory +
                        com.novelchat.data.model.InventoryItem(name = "未来彩票")
                )
            )
            storage.saveStoryState(
                characterId,
                originalStory.copy(currentRound = 2, currentLocation = "未来彩票站")
            )
            storage.saveCharacterCard(
                characterId,
                originalCard.copy(
                    mentalState = com.novelchat.data.model.MentalState(mood = "震惊")
                )
            )
            storage.saveCharacterCard(
                characterId,
                CharacterCard(id = "future_worker", name = "未来店员", occupation = "彩票店员")
            )
            storage.saveWorldbookEntry(
                characterId,
                WorldbookEntry(
                    id = "future_shop",
                    type = "location",
                    name = "未来彩票站"
                )
            )
            storage.saveNarrative(
                characterId,
                NarrativeEntry(turn = 2, userInput = "去兑奖", narrative = "旧分支兑奖剧情。")
            )
            storage.saveSummary(characterId, SummaryEntry(turn = 2, summary = "旧分支摘要。"))
            assertTrue(storage.saveNextRoundCheckpointIfAbsent(characterId))

            storage.restoreRoundCheckpoint(characterId, 2)

            assertEquals(originalStory, storage.loadStoryState(characterId))
            assertEquals(originalPlayer, storage.loadPlayerState(characterId))
            assertEquals(listOf(originalCard), storage.loadAllCharacterCards(characterId))
            assertEquals(
                listOf(originalWorldbook),
                storage.loadAllWorldbookEntries(characterId)
            )
            assertEquals(listOf(1), storage.loadAllNarratives(characterId).map { it.turn })
            assertEquals(listOf(1), storage.loadAllSummaries(characterId).map { it.turn })
            assertEquals(setOf(2), storage.loadRoundCheckpointTurns(characterId))
        } finally {
            storage.deleteConversation(characterId)
        }
    }

    @Test
    fun compatibilityCheckpoint_allowsLegacyRoundToBeReplacedInPlace() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = FileStorageManager(context)
        val characterId = 9_000_000_005L

        storage.deleteConversation(characterId)
        try {
            storage.initialize(characterId)
            storage.saveWorldSetting(characterId, WorldSetting(name = "旧存档兼容测试"))
            storage.savePlayerState(characterId, PlayerState(name = "林克"))
            val currentState = StoryState(
                currentRound = 3,
                currentLocation = "当前场景",
                globalFlags = mapOf("state_sync_pending_from_round" to "3")
            )
            storage.saveStoryState(characterId, currentState)
            (1..3).forEach { turn ->
                storage.saveNarrative(
                    characterId,
                    NarrativeEntry(
                        turn = turn,
                        userInput = "旧提示词$turn",
                        narrative = "旧剧情$turn"
                    )
                )
            }

            val compatibilityState = currentState.copy(
                currentRound = 1,
                globalFlags = emptyMap()
            )
            assertTrue(
                storage.saveCompatibilityRoundCheckpointIfAbsent(
                    characterId = characterId,
                    round = 2,
                    storyState = compatibilityState
                )
            )
            storage.restoreRoundCheckpoint(characterId, 2)

            assertEquals(1, storage.loadStoryState(characterId).currentRound)
            assertEquals(emptyMap<String, String>(), storage.loadStoryState(characterId).globalFlags)
            assertEquals(listOf(1), storage.loadAllNarratives(characterId).map { it.turn })
            assertEquals(setOf(2), storage.loadRoundCheckpointTurns(characterId))
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
