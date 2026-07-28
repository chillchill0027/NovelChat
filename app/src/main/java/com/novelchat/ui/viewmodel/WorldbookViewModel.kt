package com.novelchat.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novelchat.NovelChatApp
import com.novelchat.data.model.WorldbookEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WorldbookViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NovelChatApp
    private val fileStorage = app.fileStorage

    private val _entries = MutableStateFlow<List<WorldbookEntry>>(emptyList())
    val entries: StateFlow<List<WorldbookEntry>> = _entries.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _isMutating = MutableStateFlow(false)
    val isMutating: StateFlow<Boolean> = _isMutating.asStateFlow()
    private var characterId: Long = 0
    private var loadJob: Job? = null
    private var mutationJob: Job? = null
    private var loadGeneration = 0L
    private var mutationGeneration = 0L

    fun loadEntries(characterId: Long) {
        if (characterId <= 0L) {
            loadGeneration++
            mutationGeneration++
            loadJob?.cancel()
            mutationJob?.cancel()
            loadJob = null
            mutationJob = null
            this.characterId = 0L
            _entries.value = emptyList()
            _isLoading.value = false
            _isMutating.value = false
            _errorMessage.value = "故事编号无效，请返回故事库重试。"
            return
        }
        val storyChanged = this.characterId != characterId
        if (!storyChanged && loadJob?.isActive == true) return
        if (storyChanged) {
            loadJob?.cancel()
            mutationGeneration++
            mutationJob?.cancel()
            mutationJob = null
            _isMutating.value = false
        }
        this.characterId = characterId
        if (storyChanged) _entries.value = emptyList()
        _errorMessage.value = null
        _isLoading.value = true
        val generation = ++loadGeneration
        loadJob = viewModelScope.launch {
            try {
                val loaded = fileStorage.loadAllWorldbookEntries(characterId)
                if (this@WorldbookViewModel.characterId == characterId && loadGeneration == generation) {
                    _entries.value = loaded
                }
            } catch (e: Exception) {
                e.rethrowCancellation()
                if (this@WorldbookViewModel.characterId == characterId && loadGeneration == generation) {
                    showError(UserVisibleTextPolicy.failure("世界书加载失败", e.message))
                }
            } finally {
                if (loadGeneration == generation) {
                    _isLoading.value = false
                    loadJob = null
                }
            }
        }
    }

    fun getEntriesByType(type: String): List<WorldbookEntry> {
        return _entries.value.filter { it.type == type }
    }

    fun saveEntry(
        existing: WorldbookEntry?,
        type: String,
        name: String,
        description: String,
        keywords: List<String>,
        status: String,
        isActive: Boolean,
        onSaved: () -> Unit = {}
    ) {
        val targetCharacterId = characterId
        if (targetCharacterId == 0L || mutationJob?.isActive == true) return
        val validationError = WorldbookEntryInputPolicy.validationError(
            type = type,
            name = name,
            description = description,
            keywords = keywords,
            status = status
        )
        if (validationError != null) {
            showError(validationError)
            return
        }
        val normalized = WorldbookEntryInputPolicy.normalize(type, name, description, keywords, status)
        val duplicateError = WorldbookEntryInputPolicy.duplicateNameError(
            existingId = existing?.id,
            candidateName = normalized.name,
            entries = _entries.value
        )
        if (duplicateError != null) {
            showError(duplicateError)
            return
        }
        _errorMessage.value = null
        _isMutating.value = true
        val generation = ++mutationGeneration
        mutationJob = viewModelScope.launch {
            try {
                val now = fileStorage.getCurrentTimeString()
                val entry = (existing ?: WorldbookEntry(
                    id = WorldbookEntryInputPolicy.newEntryId(),
                    createdBy = "user",
                    createdAt = now
                )).copy(
                    type = normalized.type,
                    name = normalized.name,
                    description = normalized.description,
                    keywords = normalized.keywords,
                    status = normalized.status,
                    isActive = isActive,
                    lastModified = now
                )
                fileStorage.saveWorldbookEntry(targetCharacterId, entry)
                val loaded = fileStorage.loadAllWorldbookEntries(targetCharacterId)
                if (this@WorldbookViewModel.characterId == targetCharacterId && mutationGeneration == generation) {
                    _entries.value = loaded
                    onSaved()
                }
            } catch (e: Exception) {
                e.rethrowCancellation()
                if (this@WorldbookViewModel.characterId == targetCharacterId && mutationGeneration == generation) {
                    showError(UserVisibleTextPolicy.failure("世界书保存失败", e.message))
                }
            } finally {
                if (mutationGeneration == generation) {
                    _isMutating.value = false
                    mutationJob = null
                }
            }
        }
    }

    fun deleteEntry(entry: WorldbookEntry, onDeleted: () -> Unit = {}) {
        val targetCharacterId = characterId
        if (targetCharacterId == 0L || mutationJob?.isActive == true) return
        _isMutating.value = true
        val generation = ++mutationGeneration
        mutationJob = viewModelScope.launch {
            try {
                fileStorage.deleteWorldbookEntry(targetCharacterId, entry.id)
                val loaded = fileStorage.loadAllWorldbookEntries(targetCharacterId)
                if (this@WorldbookViewModel.characterId == targetCharacterId && mutationGeneration == generation) {
                    _entries.value = loaded
                    onDeleted()
                }
            } catch (e: Exception) {
                e.rethrowCancellation()
                if (this@WorldbookViewModel.characterId == targetCharacterId && mutationGeneration == generation) {
                    showError(UserVisibleTextPolicy.failure("世界书删除失败", e.message))
                }
            } finally {
                if (mutationGeneration == generation) {
                    _isMutating.value = false
                    mutationJob = null
                }
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun showError(message: String) {
        _errorMessage.value = message
    }
}
