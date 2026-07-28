package com.novelchat.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novelchat.NovelChatApp
import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.CharacterNamingPolicy
import com.novelchat.data.model.PlayerState
import com.novelchat.data.model.WorldbookEntry
import com.novelchat.data.remote.model.ChatMessage
import com.novelchat.data.remote.model.PlayerModificationPatch
import com.novelchat.domain.engine.PlayerPromptSnapshotPolicy
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CharacterConfirmState(
    val confirmationMessage: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * 角色确认 ViewModel
 * 对齐设计文档 11.5 节
 */
class CharacterConfirmViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NovelChatApp
    private val fileStorage = app.fileStorage
    private val indexStore = app.conversationIndexStore
    private val aiService = app.aiService
    private val gson = com.google.gson.Gson()

    private val _uiState = MutableStateFlow(CharacterConfirmState())
    val uiState: StateFlow<CharacterConfirmState> = _uiState.asStateFlow()

    private var characterId: Long = 0

    fun initialize(characterId: Long) {
        if (characterId <= 0L) {
            this.characterId = 0L
            _uiState.value = CharacterConfirmState(
                isLoading = false,
                errorMessage = "故事编号无效，请返回故事库重试。"
            )
            return
        }
        if (
            this.characterId == characterId &&
            (_uiState.value.isLoading || _uiState.value.confirmationMessage.isNotBlank())
        ) return
        this.characterId = characterId
        generateConfirmation()
    }

    private fun generateConfirmation() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val worldSetting = fileStorage.loadWorldSetting(characterId)
                val playerState = fileStorage.loadPlayerState(characterId)

                val prompt = buildConfirmationPrompt(worldSetting, playerState)
                val response = aiService.chatComplete(
                    messages = listOf(ChatMessage(role = "user", content = prompt)),
                    temperature = 0.45f,
                    maxTokens = 700
                )
                _uiState.value = _uiState.value.copy(
                    confirmationMessage = CharacterConfirmPolicy.confirmationText(
                        response = response,
                        fallback = buildFallbackConfirmation(playerState)
                    ),
                    isLoading = false
                )
            } catch (e: Exception) {
                e.rethrowCancellation()
                // 降级：使用预设文案
                val player = try {
                    fileStorage.loadPlayerState(characterId)
                } catch (storageError: Exception) {
                    storageError.rethrowCancellation()
                    null
                }
                _uiState.value = _uiState.value.copy(
                    confirmationMessage = buildFallbackConfirmation(player),
                    isLoading = false,
                    errorMessage = "AI 生成失败，已使用预设文案"
                )
            }
        }
    }

    fun confirmModify(userInput: String) {
        val normalizedInput = CharacterConfirmPolicy.normalizeModificationDraft(userInput).trim()
        if (normalizedInput.isBlank() || _uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val worldSetting = fileStorage.loadWorldSetting(characterId)
                val playerState = fileStorage.loadPlayerState(characterId)

                // 让 AI 根据用户修改意见更新角色
                val modifyPrompt = buildModifyPrompt(worldSetting, playerState, normalizedInput)
                val response = aiService.chatComplete(
                    messages = listOf(ChatMessage(role = "user", content = modifyPrompt)),
                    temperature = 0.25f,
                    maxTokens = 700,
                    jsonMode = true
                )

                // 尝试解析 JSON 中的 player 更新
                val parser = com.novelchat.domain.parser.JsonResponseParser()
                val patch = parser.parse(response, PlayerModificationPatch())
                if (patch.name?.isNotBlank() == true && CharacterNamingPolicy.isGenericPlayerName(patch.name)) {
                    throw IllegalArgumentException("主角需要使用故事中的具体姓名")
                }
                val currentPlayer = playerState
                    ?: throw IllegalStateException("未找到主角存档")
                val updated = PlayerModificationPolicy.apply(currentPlayer, patch)
                if (updated == currentPlayer) {
                    throw IllegalArgumentException("没有识别到可保存的角色修改")
                }
                savePlayerAndReferences(currentPlayer, updated)

                // 生成新的确认消息
                val newWorld = fileStorage.loadWorldSetting(characterId)
                val newPlayer = fileStorage.loadPlayerState(characterId)
                val newPrompt = buildConfirmationPrompt(newWorld, newPlayer)
                var confirmationWarning: String? = null
                val newResponse = try {
                    aiService.chatComplete(
                        messages = listOf(ChatMessage(role = "user", content = newPrompt)),
                        temperature = 0.45f,
                        maxTokens = 700
                    )
                } catch (e: Exception) {
                    e.rethrowCancellation()
                    confirmationWarning = "角色修改已保存，确认文案暂用本地版本"
                    buildFallbackConfirmation(newPlayer)
                }

                _uiState.value = _uiState.value.copy(
                    confirmationMessage = CharacterConfirmPolicy.confirmationText(
                        response = newResponse,
                        fallback = buildFallbackConfirmation(newPlayer)
                    ),
                    isLoading = false,
                    errorMessage = confirmationWarning
                )
            } catch (e: Exception) {
                e.rethrowCancellation()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = CharacterConfirmPolicy.failureMessage(e.message)
                )
            }
        }
    }

    private fun buildFallbackConfirmation(player: com.novelchat.data.model.PlayerState?): String {
        return buildString {
            appendLine("你的角色设定如下：")
            if (player != null) {
                appendLine("姓名：${player.name}")
                appendLine("年龄：${player.age}")
                appendLine("性别：${player.gender}")
                appendLine("种族：${player.race}")
                appendLine("职业：${player.class_}")
                appendLine("性格：${player.personality}")
                appendLine("背景：${player.background}")
                if (player.skills.isNotEmpty()) {
                    appendLine("技能：${player.skills.joinToString("、") { it.name }}")
                }
            }
            appendLine()
            appendLine("确认无误后即可开始故事；如果需要修改，请直接描述要调整的内容。")
        }
    }

    private suspend fun savePlayerAndReferences(current: PlayerState, updated: PlayerState) {
        if (current.name == updated.name) {
            fileStorage.savePlayerState(characterId, updated)
            return
        }

        val oldName = current.name.trim()
        val newName = updated.name.trim()
        require(newName.isNotBlank() && !CharacterNamingPolicy.isGenericPlayerName(newName)) {
            "主角需要使用故事中的具体姓名"
        }

        val storedCards = fileStorage.loadAllCharacterCards(characterId)
        require(storedCards.none { it.name == newName }) {
            "主角姓名不能与已有角色“$newName”重复"
        }
        val cardUpdates = storedCards.mapNotNull { card ->
            renamePlayerReference(card, oldName, newName).takeIf { it != card }?.let { card to it }
        }
        val storedWorldbook = fileStorage.loadAllWorldbookEntries(characterId)
        val worldbookUpdates = storedWorldbook.mapNotNull { entry ->
            renamePlayerReference(entry, oldName, newName).takeIf { it != entry }?.let { entry to it }
        }

        try {
            fileStorage.savePlayerState(characterId, updated.copy(name = newName))
            cardUpdates.forEach { (_, renamed) ->
                fileStorage.saveCharacterCard(characterId, renamed)
            }
            worldbookUpdates.forEach { (_, renamed) ->
                fileStorage.saveWorldbookEntry(characterId, renamed)
            }
            indexStore.update { entries ->
                entries.map { entry ->
                    if (entry.id == characterId) entry.copy(characterName = newName) else entry
                }
            }
        } catch (e: Exception) {
            withContext(NonCancellable) {
                runCatching { fileStorage.savePlayerState(characterId, current) }
                cardUpdates.forEach { (stored, _) ->
                    runCatching { fileStorage.saveCharacterCard(characterId, stored) }
                }
                worldbookUpdates.forEach { (stored, _) ->
                    runCatching { fileStorage.saveWorldbookEntry(characterId, stored) }
                }
            }
            throw e
        }
    }

    private fun renamePlayerReference(card: CharacterCard, oldName: String, newName: String): CharacterCard {
        val oldRelationship = card.relationships[oldName]
        val relationships = if (oldRelationship == null) {
            card.relationships
        } else {
            card.relationships.toMutableMap().apply {
                remove(oldName)
                putIfAbsent(newName, oldRelationship)
            }
        }
        return card.copy(
            occupation = card.occupation.replace(oldName, newName),
            background = card.background.replace(oldName, newName),
            relationships = relationships
        )
    }

    private fun renamePlayerReference(
        entry: WorldbookEntry,
        oldName: String,
        newName: String
    ): WorldbookEntry {
        return entry.copy(
            description = entry.description.replace(oldName, newName),
            keywords = entry.keywords.map { keyword ->
                if (keyword == oldName) newName else keyword.replace(oldName, newName)
            }.distinct(),
            relatedCharacters = entry.relatedCharacters.map { name ->
                if (name == oldName) newName else name
            }.distinct()
        )
    }

    private fun buildConfirmationPrompt(worldSetting: com.novelchat.data.model.WorldSetting?, player: com.novelchat.data.model.PlayerState?): String {
        return """【系统提示】
你是一个角色确认AI。你的任务是向用户确认他的角色设定。
要求：
- 用第二人称向用户描述他的角色
- 描述要生动，让用户有代入感
- 最后简短提示：确认无误即可开始故事；如需修改，直接描述要调整的内容
- 严格遵守世界整体设定

【世界整体设定】
${gson.toJson(worldSetting)}

【主角状态】
${player?.let { gson.toJson(PlayerPromptSnapshotPolicy.create(it)) } ?: "null"}

【请生成角色确认消息】"""
    }

    private fun buildModifyPrompt(worldSetting: com.novelchat.data.model.WorldSetting?, player: com.novelchat.data.model.PlayerState?, userInput: String): String {
        return """【系统提示】
你是一个角色修改AI。用户希望修改角色设定，请根据用户的意见更新角色。

【世界整体设定】
${gson.toJson(worldSetting)}

【当前角色状态】
${player?.let { gson.toJson(PlayerPromptSnapshotPolicy.create(it)) } ?: "null"}

【用户的修改意见】
$userInput

请输出更新后的角色JSON，只包含需要修改的字段：
{
  "name": "名字",
  "age": 25,
  "gender": "性别",
  "race": "种族",
  "class": "职业",
  "personality": "性格",
  "background": "背景",
  "appearance": "外貌",
  "skills": ["技能1", "技能2"]
}"""
    }
}
