package com.novelchat.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novelchat.NovelChatApp
import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.CharacterIdentityPolicy
import com.novelchat.data.model.CharacterFieldDefinition
import com.novelchat.domain.engine.CharacterProfilePolicy
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CharacterUiState(
    val playerName: String = "",
    val characterFields: List<CharacterFieldDefinition> = emptyList(),
    val activeCharacters: List<CharacterCard> = emptyList(),
    val poolCharacters: List<CharacterCard> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class CharacterViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NovelChatApp
    private val repo = app.storyRepository

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
                val (playerName, characterFields, all) = repo.edit(characterId) { storage ->
                    val name = storage.loadPlayerState(characterId)?.name.orEmpty()
                    val fields = storage.loadWorldSetting(characterId)?.characterFields.orEmpty()
                    val cards = CharacterIdentityPolicy.reconcileRoster(
                        storage.loadAllCharacterCards(characterId)
                    ).cards.map { card ->
                        CharacterProfilePolicy.reconcile(card, name)
                    }
                    Triple(name, fields, cards)
                }
                if (
                    this@CharacterViewModel.characterId != characterId ||
                    loadGeneration != generation
                ) return@launch
                _uiState.value = CharacterUiState(
                    playerName = playerName,
                    characterFields = characterFields,
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

    fun updateSpeechProfile(
        cardReference: String,
        speechStyle: String,
        sampleDialogues: List<String>,
        onSaved: () -> Unit = {}
    ) {
        val targetCharacterId = characterId
        if (targetCharacterId <= 0L) return
        val normalizedStyle = speechStyle.trim().take(200)
        val normalizedSamples = sampleDialogues
            .map { it.trim().take(120) }
            .filter { it.isNotEmpty() }
            .take(5)
        viewModelScope.launch {
            try {
                val updatedResult = repo.edit(targetCharacterId) { storage ->
                    val playerName = storage.loadPlayerState(targetCharacterId)?.name.orEmpty()
                    val characterFields = storage.loadWorldSetting(targetCharacterId)?.characterFields.orEmpty()
                    val all = CharacterIdentityPolicy.reconcileRoster(
                        storage.loadAllCharacterCards(targetCharacterId)
                    ).cards.map { card ->
                        CharacterProfilePolicy.reconcile(card, playerName)
                    }
                    val target = all.find { it.id.isNotBlank() && it.id == cardReference }
                        ?: all.find { it.name == cardReference }
                        ?: return@edit null
                    val updatedCard = target.copy(
                        lore = target.lore.copy(
                            speechStyle = normalizedStyle,
                            sampleDialogues = normalizedSamples
                        )
                    )
                    storage.saveCharacterCard(targetCharacterId, updatedCard)
                    val refreshed = all.map { if (it.name == updatedCard.name) updatedCard else it }
                    Triple(playerName, characterFields, refreshed)
                }
                if (updatedResult != null && this@CharacterViewModel.characterId == targetCharacterId) {
                    val (playerName, characterFields, refreshed) = updatedResult
                    _uiState.value = _uiState.value.copy(
                        playerName = playerName,
                        characterFields = characterFields,
                        activeCharacters = refreshed.filter { it.status == "active" },
                        poolCharacters = refreshed.filter { it.status == "pool" }
                    )
                    onSaved()
                }
            } catch (e: Exception) {
                e.rethrowCancellation()
                _uiState.value = _uiState.value.copy(
                    errorMessage = UserVisibleTextPolicy.failure("角色口吻保存失败", e.message)
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
