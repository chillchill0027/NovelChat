package com.novelchat

import android.app.Application
import android.util.Log
import com.novelchat.data.remote.AiService
import com.novelchat.data.storage.FileStorageManager
import com.novelchat.data.storage.ConversationIndexStore
import com.novelchat.data.storage.SettingsRepository
import com.novelchat.domain.engine.RpgGameEngine
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NovelChatApp : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var aiService: AiService
        private set
    lateinit var fileStorage: FileStorageManager
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var conversationIndexStore: ConversationIndexStore
        private set
    lateinit var gameEngine: RpgGameEngine
        private set
    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        applicationScope.launch {
            try {
                settingsRepository.migrateLegacyCredentials()
            } catch (e: Exception) {
                Log.w("NovelChatApp", "Credential migration could not be completed", e)
            }
        }
        aiService = AiService(this)
        fileStorage = FileStorageManager(this)
        conversationIndexStore = ConversationIndexStore(File(filesDir, "conversations_index.json"))
        gameEngine = RpgGameEngine(aiService, fileStorage)
    }
}
