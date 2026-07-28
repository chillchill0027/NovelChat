package com.novelchat.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novelchat.NovelChatApp
import com.novelchat.data.model.*
import com.novelchat.data.storage.ConversationIndexPolicy
import com.novelchat.domain.engine.CharacterProfilePolicy
import com.novelchat.domain.engine.GameEvent
import com.novelchat.domain.engine.PendingEventPolicy
import com.novelchat.domain.engine.PlayerGoalPolicy
import com.novelchat.domain.engine.StateSyncPolicy
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NovelChatApp
    val fileStorage = app.fileStorage
    private val gameEngine = app.gameEngine
    private val indexStore = app.conversationIndexStore

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var characterId: Long = 0
    private var engineJob: Job? = null
    private var loadJob: Job? = null
    private var autoOpened = false
    private var engineGeneration = 0L

    fun initialize(charId: Long) {
        if (charId <= 0L) {
            _uiState.value = ChatUiState(errorMessage = "故事编号无效，请返回故事库重试。")
            return
        }
        val sameStory = characterId == charId
        if (
            sameStory &&
            (loadJob?.isActive == true ||
                _uiState.value.isStreaming ||
                _uiState.value.messages.isNotEmpty() ||
                autoOpened)
        ) return
        if (!sameStory) {
            engineGeneration++
            engineJob?.cancel()
            autoOpened = false
            _uiState.value = ChatUiState(statusText = "正在加载故事...")
        }
        characterId = charId
        loadState()
    }

    private fun loadState() {
        loadJob?.cancel()
        val targetCharacterId = characterId
        loadJob = viewModelScope.launch {
            try {
                check(fileStorage.isInitialized(targetCharacterId)) {
                    "故事存档不存在或尚未完成初始化，请返回故事库重试"
                }
                val storedPlayerState = fileStorage.loadPlayerState(targetCharacterId)
                var playerState = storedPlayerState
                    ?: PlayerState(name = CharacterNamingPolicy.DEFAULT_PLAYER_NAME)
                if (storedPlayerState == null) {
                    fileStorage.savePlayerState(targetCharacterId, playerState)
                }
                var storyState = fileStorage.loadStoryState(targetCharacterId)
                val narratives = fileStorage.loadAllNarratives(targetCharacterId)
                StateSyncPolicy.reconcilePersistedNarratives(storyState, narratives).let { recovered ->
                    if (recovered != storyState) {
                        storyState = recovered
                        fileStorage.saveStoryState(targetCharacterId, storyState)
                    }
                }
                narratives.lastOrNull()?.let { latest ->
                    val prioritizedPending = PendingEventPolicy.prioritizeImmediateConsequences(
                        events = storyState.pendingEvents,
                        narrative = latest.narrative
                    )
                    if (prioritizedPending != storyState.pendingEvents) {
                        storyState = storyState.copy(pendingEvents = prioritizedPending)
                        fileStorage.saveStoryState(targetCharacterId, storyState)
                    }
                    val aligned = PlayerGoalPolicy.alignWithPendingEvents(playerState, storyState.pendingEvents)
                    if (aligned != playerState) {
                        playerState = aligned
                        fileStorage.savePlayerState(targetCharacterId, aligned)
                    }
                }
                val playerName = playerState.name
                val storedActiveCards = fileStorage.loadActiveCharacterCards(targetCharacterId)
                val activeCards = storedActiveCards
                    .map { card -> CharacterProfilePolicy.reconcile(card, playerName) }
                storedActiveCards.zip(activeCards).forEach { (stored, reconciled) ->
                    if (stored != reconciled) fileStorage.saveCharacterCard(targetCharacterId, reconciled)
                }
                narratives.lastOrNull()?.let { latest ->
                    val reconciledActiveCharacters = SceneCharacterPolicy.reconcileEmptyActiveCharacters(
                        cards = activeCards,
                        activeCharacters = storyState.activeCharacters,
                        playerName = playerName,
                        latestNarrative = latest.narrative
                    )
                    if (reconciledActiveCharacters != storyState.activeCharacters) {
                        storyState = storyState.copy(activeCharacters = reconciledActiveCharacters)
                        fileStorage.saveStoryState(targetCharacterId, storyState)
                    }
                }
                val npcCards = visibleNpcCards(
                    cards = activeCards,
                    storyState = storyState,
                    playerName = playerName
                )
                val worldbookEntries = fileStorage.loadActiveWorldbookEntries(targetCharacterId)
                val messages = narratives.flatMap { n ->
                    listOf(ChatMessageItem(turn = n.turn, role = "user", content = n.userInput),
                           ChatMessageItem(turn = n.turn, role = "assistant", content = n.narrative))
                }
                if (characterId != targetCharacterId) return@launch
                _uiState.value = ChatUiState(
                    messages = messages, playerState = playerState, storyState = storyState,
                    npcCards = npcCards, worldbookEntries = worldbookEntries,
                    currentRound = storyState.currentRound,
                    isReady = true
                )

                // 自动开场白：首次进入且无消息时
                if (messages.isEmpty() && !autoOpened) {
                    autoOpened = true
                    sendMessage("开始这一天")
                }
            } catch (e: Exception) {
                e.rethrowCancellation()
                if (characterId == targetCharacterId) {
                    _uiState.value = _uiState.value.copy(
                        statusText = "",
                        isReady = false,
                        errorMessage = UserVisibleTextPolicy.failure("加载状态失败", e.message)
                    )
                }
            }
        }
    }

    fun sendMessage(content: String) {
        if (_uiState.value.isStreaming) return
        val validation = ChatInputPolicy.validate(
            content = content,
            isReady = _uiState.value.isReady,
            pendingSyncRound = _uiState.value.storyState?.let(StateSyncPolicy::pendingFromRound)
        )
        validation.errorMessage?.let { message ->
            _uiState.value = _uiState.value.copy(errorMessage = message)
            return
        }
        val trimmed = validation.action ?: return
        engineJob?.cancel()
        val targetCharacterId = characterId
        val generation = ++engineGeneration
        val nextTurn = _uiState.value.currentRound + 1
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + ChatMessageItem(
                turn = nextTurn,
                role = "user",
                content = trimmed
            ),
            isStreaming = true,
            canCancelGeneration = true,
            errorMessage = null,
            lastFailedAction = null,
            statusText = "正在生成..."
        )
        engineJob = viewModelScope.launch {
            try {
                gameEngine.executeRound(targetCharacterId, trimmed).collect { event ->
                    if (characterId == targetCharacterId && engineGeneration == generation) {
                        handleEvent(event, targetCharacterId, generation)
                    }
                }
            } catch (_: CancellationException) {
                // The UI has already replaced the partial response with a retryable message.
            } catch (e: Exception) {
                if (characterId == targetCharacterId && engineGeneration == generation) {
                    showRoundError("引擎错误：${e.message}")
                }
            }
        }
    }

    private fun handleEvent(event: GameEvent, targetCharacterId: Long, generation: Long) {
        val cur = _uiState.value
        when (event) {
            is GameEvent.Loading -> _uiState.value = cur.copy(statusText = event.message)
            is GameEvent.NarrativeChunk -> {
                val msgs = cur.messages.toMutableList()
                val last = msgs.lastOrNull()
                if (last?.isStreaming == true && last.role == "assistant") msgs[msgs.size - 1] = last.copy(content = last.content + event.chunk)
                else msgs.add(ChatMessageItem(turn = cur.currentRound + 1, role = "assistant", content = event.chunk, isStreaming = true))
                _uiState.value = cur.copy(messages = msgs, isStreaming = true)
            }
            is GameEvent.NarrativeComplete -> {
                val msgs = cur.messages.toMutableList()
                val idx = msgs.indexOfLast { it.isStreaming && it.role == "assistant" }
                if (idx >= 0) msgs[idx] = msgs[idx].copy(content = event.narrative, isStreaming = false)
                else msgs.add(ChatMessageItem(turn = cur.currentRound + 1, role = "assistant", content = event.narrative))
                _uiState.value = cur.copy(
                    messages = msgs,
                    statusText = "剧情完成，正在同步状态...",
                    canCancelGeneration = false
                )
            }
            is GameEvent.TaskComplete -> _uiState.value = cur.copy(
                statusText = "正在更新状态...",
                canCancelGeneration = false
            )
            is GameEvent.PlayerUpdated -> _uiState.value = cur.copy(playerState = event.player, statusText = "状态已更新")
            is GameEvent.CharacterCreated -> {
                _uiState.value = cur.copy(npcCards = upsertCard(cur.npcCards, event.card))
            }
            is GameEvent.CharacterUpdated -> {
                val existsOnScene = cur.npcCards.any { sameCharacter(it, event.card) }
                if (existsOnScene) {
                    _uiState.value = cur.copy(npcCards = upsertCard(cur.npcCards, event.card))
                }
            }
            is GameEvent.CharacterRetired -> {
                _uiState.value = cur.copy(npcCards = cur.npcCards.filterNot { it.name == event.cardName })
            }
            is GameEvent.WorldbookUpdated -> {
                val entries = cur.worldbookEntries.filterNot { it.id == event.entry.id }
                _uiState.value = cur.copy(
                    worldbookEntries = if (event.entry.isActive) entries + event.entry else entries
                )
            }
            is GameEvent.WorldbookDeleted -> _uiState.value = cur.copy(
                worldbookEntries = cur.worldbookEntries.filterNot { it.id == event.entryId }
            )
            is GameEvent.RoundComplete -> {
                viewModelScope.launch {
                    try {
                        val ss = fileStorage.loadStoryState(targetCharacterId)
                        val ps = fileStorage.loadPlayerState(targetCharacterId)
                        val storedNpcs = fileStorage.loadActiveCharacterCards(targetCharacterId)
                        val narratives = fileStorage.loadAllNarratives(targetCharacterId)
                        val worldbook = fileStorage.loadActiveWorldbookEntries(targetCharacterId)
                        if (characterId != targetCharacterId || engineGeneration != generation) return@launch
                        val pn = ps?.name ?: ""
                        val npcs = storedNpcs.map { card -> CharacterProfilePolicy.reconcile(card, pn) }
                        _uiState.value = _uiState.value.copy(
                            messages = narratives.flatMap { n -> listOf(ChatMessageItem(turn = n.turn, role = "user", content = n.userInput), ChatMessageItem(turn = n.turn, role = "assistant", content = n.narrative)) },
                            playerState = ps,
                            storyState = ss,
                            npcCards = visibleNpcCards(npcs, ss, pn),
                            worldbookEntries = worldbook,
                            currentRound = event.round,
                            isStreaming = false,
                            canCancelGeneration = false,
                            lastFailedAction = null,
                            statusText = "第${event.round}轮完成"
                        )
                        updateConversationIndex(targetCharacterId, event.round)
                    } catch (e: Exception) {
                        e.rethrowCancellation()
                        if (characterId != targetCharacterId || engineGeneration != generation) return@launch
                        Log.e("ChatViewModel", "Round completed but UI refresh failed", e)
                        showSavedRoundRefreshError()
                    }
                }
            }
            is GameEvent.Warning -> _uiState.value = cur.copy(
                errorMessage = ChatErrorPolicy.userVisibleWarning(event.message),
                statusText = "状态同步未完成"
            )
            is GameEvent.Error -> showRoundError(event.message)
            else -> {}
        }
    }

    private fun showRoundError(message: String?) {
        val cur = _uiState.value
        val visibleMessage = ChatErrorPolicy.userVisibleMessage(message)
        val cleanedMessages = cur.messages.filterNot { it.isStreaming && it.role == "assistant" }
        val shouldAppend = cleanedMessages.lastOrNull()?.content != visibleMessage
        _uiState.value = cur.copy(
            messages = if (shouldAppend) {
                cleanedMessages + ChatMessageItem(
                    turn = cur.currentRound + 1,
                    role = "error",
                    content = visibleMessage
                )
            } else {
                cleanedMessages
            },
            isStreaming = false,
            canCancelGeneration = false,
            errorMessage = visibleMessage,
            lastFailedAction = cur.messages.lastOrNull { it.role == "user" }?.content,
            statusText = ""
        )
    }

    private fun showSavedRoundRefreshError() {
        val message = "本轮剧情已保存，但角色卡刷新失败。点击重试只会重新读取状态，不会重复生成剧情。"
        _uiState.value = _uiState.value.copy(
            isReady = false,
            isStreaming = false,
            canCancelGeneration = false,
            errorMessage = message,
            lastFailedAction = null,
            statusText = ""
        )
    }

    private fun visibleNpcCards(
        cards: List<CharacterCard>,
        storyState: StoryState?,
        playerName: String
    ): List<CharacterCard> {
        return SceneCharacterPolicy.visibleNpcCards(
            cards = cards,
            activeCharacters = storyState?.activeCharacters.orEmpty(),
            playerName = playerName
        )
    }

    private fun upsertCard(cards: List<CharacterCard>, card: CharacterCard): List<CharacterCard> {
        val index = cards.indexOfFirst { sameCharacter(it, card) }
        if (index < 0) return cards + card
        return cards.toMutableList().apply { this[index] = card }
    }

    private fun sameCharacter(left: CharacterCard, right: CharacterCard): Boolean {
        return left.name == right.name ||
                (left.id.isNotBlank() && right.id.isNotBlank() && left.id == right.id)
    }

    private suspend fun updateConversationIndex(targetCharacterId: Long, round: Int) {
        try {
            val latest = fileStorage.loadAllNarratives(targetCharacterId).lastOrNull()
            indexStore.update { entries ->
                if (entries.none { it.id == targetCharacterId }) {
                    entries
                } else {
                    ConversationIndexPolicy.updateAfterRound(
                        entries = entries,
                        characterId = targetCharacterId,
                        latestNarrative = latest,
                        round = round,
                        now = fileStorage.getCurrentTimeString()
                    )
                }
            }
        } catch (e: Exception) {
            e.rethrowCancellation()
            Log.w("ChatViewModel", "Failed to update conversation index", e)
        }
    }

    fun cancelStream() {
        val cur = _uiState.value
        if (!cur.isStreaming || !cur.canCancelGeneration) return

        val preparation = ChatRetryPolicy.prepare(
            messages = cur.messages,
            lastFailedAction = cur.messages.lastOrNull { it.role == "user" }?.content
        )
        val cancelledMessage = "已停止本轮生成。行动内容已保留，可以直接重试。"
        engineGeneration++
        engineJob?.cancel()
        _uiState.value = cur.copy(
            messages = preparation.messages + listOfNotNull(
                preparation.action?.let { action ->
                    ChatMessageItem(
                        turn = cur.currentRound + 1,
                        role = "user",
                        content = action
                    )
                },
                ChatMessageItem(
                    turn = cur.currentRound + 1,
                    role = "error",
                    content = cancelledMessage
                )
            ),
            isStreaming = false,
            canCancelGeneration = false,
            errorMessage = null,
            lastFailedAction = preparation.action,
            statusText = ""
        )
    }

    fun retryLastAction() {
        val cur = _uiState.value
        if (cur.isStreaming) return

        val preparation = ChatRetryPolicy.prepare(cur.messages, cur.lastFailedAction)
        val action = preparation.action ?: return
        _uiState.value = cur.copy(
            messages = preparation.messages,
            errorMessage = null,
            lastFailedAction = null,
            statusText = ""
        )
        sendMessage(action)
    }

    fun retryPendingStateSync() {
        val cur = _uiState.value
        if (
            characterId <= 0L ||
            cur.isStreaming ||
            StateSyncPolicy.pendingFromRound(cur.storyState ?: return) == null
        ) return

        engineJob?.cancel()
        val targetCharacterId = characterId
        val generation = ++engineGeneration
        _uiState.value = cur.copy(
            isStreaming = true,
            canCancelGeneration = false,
            errorMessage = null,
            lastFailedAction = null,
            statusText = "正在补同步角色与场景状态..."
        )
        engineJob = viewModelScope.launch {
            try {
                gameEngine.retryPendingStateSync(targetCharacterId).collect { event ->
                    if (characterId != targetCharacterId || engineGeneration != generation) return@collect
                    if (event is GameEvent.Error) {
                        showStateSyncError(event.message)
                    } else {
                        handleEvent(event, targetCharacterId, generation)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (characterId == targetCharacterId && engineGeneration == generation) {
                    showStateSyncError(e.message)
                }
            }
        }
    }

    private fun showStateSyncError(detail: String?) {
        _uiState.value = _uiState.value.copy(
            isStreaming = false,
            canCancelGeneration = false,
            errorMessage = UserVisibleTextPolicy.failure("状态补同步失败", detail),
            statusText = "状态仍待补同步"
        )
    }

    fun retryStateRefresh() {
        if (characterId <= 0L || _uiState.value.isStreaming || loadJob?.isActive == true) return
        _uiState.value = _uiState.value.copy(
            isReady = false,
            errorMessage = null,
            statusText = "正在重新加载角色与剧情状态..."
        )
        loadState()
    }

    fun clearError() { _uiState.value = _uiState.value.copy(errorMessage = null) }
}
