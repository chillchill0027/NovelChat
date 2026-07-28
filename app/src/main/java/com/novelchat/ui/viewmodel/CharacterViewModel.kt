package com.novelchat.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novelchat.NovelChatApp
import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.CharacterIdentityPolicy
import com.novelchat.domain.engine.CharacterProfilePolicy
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CharacterUiState(
    val playerName: String = "",
    val activeCharacters: List<CharacterCard> = emptyList(),
    val poolCharacters: List<CharacterCard> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class CharacterViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NovelChatApp
    private val fileStorage = app.fileStorage

    private val _uiState = MutableStateFlow(CharacterUiState())
    val uiState: StateFlow<CharacterUiState> = _uiState.asStateFlow()

    private var characterId: Long = 0
    private var loadJob: Job? = null
    private var loadGeneration = 0L

    fun loadCharacters(characterId: Long) {
        if (characterId <= 0L) {
            loadGeneration++
            loadJob?.cancel()
            loadJob = null
            this.characterId = 0L
            _uiState.value = CharacterUiState(
                isLoading = false,
                errorMessage = "故事编号无效，请返回故事库重试。"
            )
            return
        }
        val storyChanged = this.characterId != characterId
        if (!storyChanged && loadJob?.isActive == true) return
        if (storyChanged) loadJob?.cancel()
        this.characterId = characterId
        val generation = ++loadGeneration
        loadJob = viewModelScope.launch {
            _uiState.value = if (storyChanged) {
                CharacterUiState(isLoading = true)
            } else {
                _uiState.value.copy(isLoading = true, errorMessage = null)
            }
            try {
                val playerName = fileStorage.loadPlayerState(characterId)?.name.orEmpty()
                val all = CharacterIdentityPolicy.reconcileRoster(
                    fileStorage.loadAllCharacterCards(characterId)
                ).cards.map { card ->
                    CharacterProfilePolicy.reconcile(card, playerName)
                }
                if (
                    this@CharacterViewModel.characterId != characterId ||
                    loadGeneration != generation
                ) return@launch
                _uiState.value = CharacterUiState(
                    playerName = playerName,
                    activeCharacters = all.filter { it.status == "active" },
                    poolCharacters = all.filter { it.status == "pool" },
                    isLoading = false
                )
            } catch (e: Exception) {
                e.rethrowCancellation()
                if (
                    this@CharacterViewModel.characterId != characterId ||
                    loadGeneration != generation
                ) return@launch
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = UserVisibleTextPolicy.failure("角色加载失败", e.message)
                )
            } finally {
                if (loadGeneration == generation) loadJob = null
            }
        }
    }

    fun getCardByReference(reference: String): CharacterCard? {
        val cards = _uiState.value.activeCharacters + _uiState.value.poolCharacters
        return cards.find { it.id.isNotBlank() && it.id == reference }
            ?: cards.find { it.name == reference }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
