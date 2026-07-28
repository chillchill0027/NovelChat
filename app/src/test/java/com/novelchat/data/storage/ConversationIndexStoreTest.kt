package com.novelchat.data.storage

import com.novelchat.data.model.ConversationEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ConversationIndexStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun concurrentUpdates_keepEveryConversation() = runBlocking {
        val store = ConversationIndexStore(temporaryFolder.newFile("conversations_index.json"))

        coroutineScope {
            repeat(40) { index ->
                launch(Dispatchers.Default) {
                    val conversationId = index.toLong() + 1
                    store.update { entries ->
                        entries + ConversationEntry(
                            id = conversationId,
                            characterName = "角色$conversationId"
                        )
                    }
                }
            }
        }

        val loaded = store.load()
        assertEquals(40, loaded.size)
        assertEquals((1L..40L).toSet(), loaded.map { it.id }.toSet())
    }

    @Test
    fun update_readsExistingEntriesAndPersistsReplacement() = runBlocking {
        val store = ConversationIndexStore(temporaryFolder.newFile("conversations_index.json"))
        store.update { listOf(ConversationEntry(id = 7, characterName = "林克")) }

        store.update { entries ->
            entries.map { entry -> entry.copy(messageCount = 3, lastMessage = "新的剧情") }
        }

        assertEquals(3, store.load().single().messageCount)
        assertEquals("新的剧情", store.load().single().lastMessage)
    }

    @Test
    fun update_repairsMalformedIndexEvenWhenRecoveredListIsEmpty() = runBlocking {
        val file = temporaryFolder.newFile("conversations_index.json")
        file.writeText("{not valid json")
        val store = ConversationIndexStore(file)

        store.update { it }

        assertEquals(emptyList<ConversationEntry>(), store.load())
        assertFalse(file.readText().contains("not valid"))
        assertEquals("[]", file.readText())
    }
}
