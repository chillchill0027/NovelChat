package com.novelchat.data.storage

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.novelchat.data.model.ConversationEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File

class ConversationIndexStore(
    private val indexFile: File,
    private val gson: Gson = Gson()
) {
    private val mutex = Mutex()
    private val listType = object : TypeToken<List<ConversationEntry>>() {}.type
    private var lastReadWasCorrupt = false

    suspend fun load(): List<ConversationEntry> = withContext(Dispatchers.IO) {
        mutex.lock()
        try {
            readUnlocked()
        } finally {
            mutex.unlock()
        }
    }

    suspend fun update(
        transform: suspend (List<ConversationEntry>) -> List<ConversationEntry>
    ): List<ConversationEntry> = withContext(Dispatchers.IO) {
        mutex.lock()
        try {
            val current = readUnlocked()
            val updated = ConversationIndexPolicy.sanitize(transform(current))
            if (updated != current || !indexFile.exists() || lastReadWasCorrupt) {
                AtomicTextFile.write(indexFile, gson.toJson(updated))
                lastReadWasCorrupt = false
            }
            updated
        } finally {
            mutex.unlock()
        }
    }

    private fun readUnlocked(): List<ConversationEntry> {
        val json = AtomicTextFile.readOrNull(indexFile) ?: return emptyList()
        return try {
            val parsed = gson.fromJson<List<ConversationEntry>>(json, listType)
            if (parsed == null) {
                lastReadWasCorrupt = true
                emptyList()
            } else {
                lastReadWasCorrupt = false
                ConversationIndexPolicy.sanitize(parsed)
            }
        } catch (_: Exception) {
            lastReadWasCorrupt = true
            emptyList()
        }
    }
}
