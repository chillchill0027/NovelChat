package com.novelchat.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novelchat.NovelChatApp
import com.novelchat.data.model.*
import com.novelchat.data.storage.ConversationIndexPolicy
import com.novelchat.data.storage.LegacyDataMigrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

data class LobbyOperationState(
    val isLoading: Boolean = false,
    val isCreating: Boolean = false,
    val deletingId: Long? = null,
    val errorMessage: String? = null
)

class MainLobbyViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NovelChatApp
    private val fileStorage = app.fileStorage
    private val indexStore = app.conversationIndexStore

    private val _conversations = MutableStateFlow<List<ConversationEntry>>(emptyList())
    val conversations: StateFlow<List<ConversationEntry>> = _conversations.asStateFlow()
    private val _operationState = MutableStateFlow(LobbyOperationState())
    val operationState: StateFlow<LobbyOperationState> = _operationState.asStateFlow()
    private val creationGuard = AtomicBoolean(false)
    private var loadJob: Job? = null
    private var loadGeneration = 0L

    fun loadConversations() {
        if (
            loadJob?.isActive == true ||
            creationGuard.get() ||
            _operationState.value.deletingId != null
        ) return
        val generation = ++loadGeneration
        loadJob = viewModelScope.launch {
            _operationState.value = _operationState.value.copy(isLoading = true)
            try {
                val loaded = loadIndex()
                if (loadGeneration == generation) _conversations.value = loaded
            } catch (e: Exception) {
                e.rethrowCancellation()
                if (loadGeneration == generation) {
                    showError(UserVisibleTextPolicy.failure("故事库加载失败", e.message))
                }
            } finally {
                if (loadGeneration == generation) {
                    _operationState.value = _operationState.value.copy(isLoading = false)
                    loadJob = null
                }
            }
        }
    }

    fun createConversation(
        name: String, desc: String, card: CharacterCard? = null,
        world: WorldSetting = defaultModernWorldSetting,
        player: PlayerState = PlayerState(name = CharacterNamingPolicy.DEFAULT_PLAYER_NAME, charm = 50, huntingSkill = 50, socialRank = "C"),
        presetKey: String = "",
        openingPrompt: String = "",
        onCreated: (Long) -> Unit
    ) {
        if (_operationState.value.deletingId != null) return
        if (!creationGuard.compareAndSet(false, true)) return
        invalidateLoad()
        viewModelScope.launch {
            _operationState.value = _operationState.value.copy(
                isCreating = true,
                errorMessage = null
            )
            try {
                val id = try {
                    doCreateConversation(name, desc, card, world, player, presetKey, openingPrompt)
                } catch (e: Exception) {
                    e.rethrowCancellation()
                    showError(UserVisibleTextPolicy.failure("创建故事失败", e.message))
                    return@launch
                }
                try {
                    _conversations.value = loadIndex()
                } catch (e: Exception) {
                    e.rethrowCancellation()
                    showError(UserVisibleTextPolicy.failure("故事已创建，但列表刷新失败", e.message))
                }
                _operationState.value = _operationState.value.copy(isCreating = false)
                try {
                    onCreated(id)
                } catch (e: Exception) {
                    e.rethrowCancellation()
                    showError(UserVisibleTextPolicy.failure("故事已创建，但页面打开失败", e.message))
                }
            } finally {
                creationGuard.set(false)
                _operationState.value = _operationState.value.copy(isCreating = false)
            }
        }
    }

    private suspend fun doCreateConversation(
        name: String, desc: String, card: CharacterCard? = null,
        world: WorldSetting = defaultModernWorldSetting, player: PlayerState = PlayerState(name = CharacterNamingPolicy.DEFAULT_PLAYER_NAME),
        presetKey: String = "",
        openingPrompt: String = ""
    ): Long = withContext(Dispatchers.IO) {
        val characterId = fileStorage.createConversationStorage()
        try {
            fileStorage.saveWorldSetting(characterId, world)
            fileStorage.savePlayerState(characterId, player)
            if (card != null) fileStorage.saveCharacterCard(characterId, card)
            if (world.name == defaultModernWorldSetting.name) {
                seedDefaultModernStory(characterId, fileStorage.getCurrentTimeString())
            }
            if (openingPrompt.isNotBlank()) {
                val currentState = fileStorage.loadStoryState(characterId)
                fileStorage.saveStoryState(
                    characterId,
                    CustomOpeningPolicy.apply(currentState, openingPrompt)
                )
            }
            fileStorage.completeConversationStorage(characterId)

            val entry = ConversationEntry(
                id = characterId,
                characterName = name.trim().ifBlank { CharacterNamingPolicy.DEFAULT_PLAYER_NAME },
                characterDesc = desc.trim().ifBlank { "自定义城市开局" },
                type = if (presetKey.isNotBlank()) "preset" else "custom",
                presetKey = presetKey, createdAt = fileStorage.getCurrentTimeString(),
                lastMessageAt = fileStorage.getCurrentTimeString()
            )
            addToIndex(entry)
            characterId
        } catch (e: Exception) {
            withContext(NonCancellable) {
                runCatching { removeFromIndex(characterId) }
                runCatching { fileStorage.deleteConversation(characterId) }
            }
            throw e
        }
    }

    private suspend fun seedDefaultModernStory(characterId: Long, createdAt: String) {
        defaultModernCharacters(createdAt).forEach { fileStorage.saveCharacterCard(characterId, it) }
        defaultModernWorldbookEntries(createdAt).forEach { fileStorage.saveWorldbookEntry(characterId, it) }
        fileStorage.saveStoryState(characterId, defaultModernStoryState())
    }

    fun deleteConversation(id: Long) {
        if (_operationState.value.deletingId != null || creationGuard.get()) return
        invalidateLoad()
        viewModelScope.launch {
            _operationState.value = _operationState.value.copy(
                deletingId = id,
                errorMessage = null
            )
            try {
                withContext(NonCancellable) {
                    app.storySessionManager.closeSession(id)
                    fileStorage.deleteConversation(id)
                    removeFromIndex(id)
                }
                _conversations.value = loadIndex()
            } catch (e: Exception) {
                e.rethrowCancellation()
                showError(UserVisibleTextPolicy.failure("删除故事失败", e.message))
            } finally {
                _operationState.value = _operationState.value.copy(deletingId = null)
            }
        }
    }

    fun clearOperationError() {
        _operationState.value = _operationState.value.copy(errorMessage = null)
    }

    private fun showError(message: String) {
        _operationState.value = _operationState.value.copy(errorMessage = message)
    }

    private fun invalidateLoad() {
        loadGeneration++
        loadJob?.cancel()
        loadJob = null
        _operationState.value = _operationState.value.copy(isLoading = false)
    }

    private suspend fun loadIndex(): List<ConversationEntry> = withContext(Dispatchers.IO) {
        LegacyDataMigrator(fileStorage, indexStore).migrateConversationIndex()
        ConversationIndexPolicy.sort(indexStore.load())
    }

    private suspend fun addToIndex(e: ConversationEntry) {
        indexStore.update { entries ->
            listOf(e) + entries.filterNot { it.id == e.id }
        }
    }

    private suspend fun removeFromIndex(id: Long) {
        indexStore.update { entries -> entries.filterNot { it.id == id } }
    }
}
