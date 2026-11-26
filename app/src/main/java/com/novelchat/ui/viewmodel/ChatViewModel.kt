package com.novelchat.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novelchat.NovelChatApp
import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.CreativityLevel
import com.novelchat.data.model.NarrationSettings
import com.novelchat.data.model.NarrativeEntry
import com.novelchat.data.model.StoryState
import com.novelchat.data.storage.ConversationIndexPolicy
import com.novelchat.domain.v2.context.PlayerInput
import com.novelchat.session.RoundPhase
import com.novelchat.session.StorySession
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NovelChatApp
    val fileStorage = app.fileStorage
    private val indexStore = app.conversationIndexStore
    private val repo = app.storyRepository
    private val sessionManager = app.storySessionManager
    private val settingsRepository = app.settingsRepository
    private val llmMetrics = app.llmMetrics

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val localError = MutableStateFlow<String?>(null)
    private val dismissedSyncRounds = MutableStateFlow<Set<Int>>(emptySet())

    private var characterId: Long = 0
    private var loadJob: Job? = null
    private var sessionObserverJob: Job? = null
    private var activeSession: StorySession? = null
    private var autoOpened = false

    fun initialize(charId: Long) {
        if (charId <= 0L) {
            _uiState.value = ChatUiState(errorMessage = "故事编号无效，请返回故事库重试。")
            return
        }
        val sameStory = characterId == charId
        if (
            sameStory &&
            (loadJob?.isActive == true ||
                sessionObserverJob?.isActive == true ||
                _uiState.value.isStreaming ||
                _uiState.value.messages.isNotEmpty() ||
                autoOpened)
        ) return
        if (!sameStory) {
            loadJob?.cancel()
            sessionObserverJob?.cancel()
            activeSession = null
            autoOpened = false
            localError.value = null
            dismissedSyncRounds.value = emptySet()
            _uiState.value = ChatUiState(statusText = "正在加载故事...")
        }
        characterId = charId
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            initializeSession(charId)
        }
    }

    fun sendMessage(content: String) {
        if (_uiState.value.isStreaming) return
        val validation = ChatInputPolicy.validate(
            content = content,
            isReady = _uiState.value.isReady,
            pendingSyncRound = null,
            enforcePendingSyncBlock = false
        )
        validation.errorMessage?.let { message ->
            localError.value = message
            _uiState.value = _uiState.value.copy(errorMessage = message)
            return
        }
        val action = validation.action ?: return
        val targetCharacterId = characterId
        val session = activeSession ?: sessionManager.session(targetCharacterId).also { activeSession = it }
        localError.value = null
        session.clearError()
        val cur = _uiState.value
        session.startRound(
            input = PlayerInput(
                raw = action,
                lengthPreference = cur.narrationSettings.resolvedLength(cur.defaultNarrationLength),
                creativity = cur.narrationCreativity
            ),
            onNarrativeCommitted = { turn, _ ->
                updateConversationIndex(targetCharacterId, turn)
            }
        )
    }

    fun regenerateRound(turn: Int, revisedAction: String) {
        val cur = _uiState.value
        if (cur.isStreaming || characterId <= 0L) return
        val validation = ChatInputPolicy.validate(
            content = revisedAction,
            isReady = cur.isReady,
            pendingSyncRound = null,
            enforcePendingSyncBlock = false
        )
        validation.errorMessage?.let { message ->
            localError.value = message
            _uiState.value = cur.copy(errorMessage = message)
            return
        }
        val action = validation.action ?: return
        val targetCharacterId = characterId
        val session = activeSession ?: sessionManager.session(targetCharacterId).also { activeSession = it }
        localError.value = null
        session.clearError()
        session.regenerate(
            turn = turn,
            revisedInput = PlayerInput(
                raw = action,
                lengthPreference = cur.narrationSettings.resolvedLength(cur.defaultNarrationLength),
                creativity = cur.narrationCreativity
            ),
            onNarrativeCommitted = { committedTurn, _ ->
                updateConversationIndex(targetCharacterId, committedTurn)
            }
        )
    }

    fun cancelStream() {
        val cur = _uiState.value
        if (!cur.isStreaming || !cur.canCancelGeneration) return
        val cancelledAction = activeSession?.state?.value?.activeUserInput?.takeIf { it.isNotBlank() }
            ?: cur.messages.lastOrNull { it.role == "user" && it.isStreaming }?.content
        activeSession?.cancelNarrative()
        val notice = "已停止本轮生成。行动内容已保留，可以直接重试。"
        localError.value = notice
        _uiState.value = cur.copy(
            isStreaming = false,
            canCancelGeneration = false,
            statusText = "",
            errorMessage = notice,
            lastFailedAction = cancelledAction ?: cur.lastFailedAction
        )
    }

    fun retryLastAction() {
        val cur = _uiState.value
        if (cur.isStreaming) return
        val action = cur.lastFailedAction?.trim()?.takeIf { it.isNotBlank() } ?: return
        localError.value = null
        activeSession?.clearError()
        sendMessage(action)
    }

    fun retryBackgroundSync() {
        if (characterId <= 0L) return
        dismissedSyncRounds.value = emptySet()
        activeSession?.retrySync()
    }

    fun dismissSyncBanner() {
        dismissedSyncRounds.value = _uiState.value.failedSyncRounds
    }

    fun retryStateRefresh() {
        if (characterId <= 0L || _uiState.value.isStreaming || loadJob?.isActive == true) return
        val targetId = characterId
        _uiState.value = _uiState.value.copy(
            isReady = false,
            errorMessage = null,
            statusText = "正在重新加载角色与剧情状态..."
        )
        loadJob = viewModelScope.launch {
            repo.invalidate(targetId)
            initializeSession(targetId)
        }
    }

    fun clearError() {
        localError.value = null
        activeSession?.clearError()
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun updateNarrationSettings(
        settings: NarrationSettings,
        creativity: CreativityLevel = _uiState.value.narrationCreativity
    ) {
        val targetId = characterId
        if (targetId <= 0L) return
        val normalized = settings.normalized()
        _uiState.value = _uiState.value.copy(
            narrationSettings = normalized,
            narrationCreativity = creativity
        )
        viewModelScope.launch {
            try {
                repo.updateNarrationSettings(targetId, normalized)
                if (creativity != settingsRepository.narrationCreativity.first()) {
                    settingsRepository.saveNarrationCreativity(creativity)
                }
            } catch (e: Exception) {
                e.rethrowCancellation()
                Log.w("ChatViewModel", "Failed to save narration settings", e)
            }
        }
    }

    fun loadPerfDebugSnapshot(): PerfDebugSnapshot {
        val sessionState = activeSession?.state?.value
        return PerfDebugSnapshot(
            recentCalls = llmMetrics.recentCalls(30).asReversed(),
            recentRounds = llmMetrics.recentRounds(20).asReversed(),
            lastContextDebug = sessionState?.lastDebugInfo,
            syncingRounds = sessionState?.sync?.syncingRounds ?: _uiState.value.syncingRounds,
            failedRounds = sessionState?.sync?.failedRounds ?: _uiState.value.failedSyncRounds
        )
    }

    private suspend fun initializeSession(targetCharacterId: Long) {
        try {
            val session = sessionManager.session(targetCharacterId)
            activeSession = session
            session.open()
            repo.snapshot(targetCharacterId)
            startSessionObserver(targetCharacterId, session)
        } catch (e: Exception) {
            e.rethrowCancellation()
            Log.e("ChatViewModel", "Failed to load story session", e)
            _uiState.value = _uiState.value.copy(
                isReady = false,
                statusText = "",
                errorMessage = UserVisibleTextPolicy.failure("加载故事失败", e.message)
            )
        }
    }

    private fun startSessionObserver(targetCharacterId: Long, session: StorySession) {
        sessionObserverJob?.cancel()
        sessionObserverJob = viewModelScope.launch {
            val globalPrefsFlow = combine(
                settingsRepository.narrationLength,
                settingsRepository.narrationCreativity
            ) { length, creativity -> length to creativity }
            val uiOverlayFlow = combine(
                localError,
                dismissedSyncRounds
            ) { currentLocalError, dismissedFailed -> currentLocalError to dismissedFailed }

            combine(
                repo.snapshotFlow(targetCharacterId),
                session.state,
                globalPrefsFlow,
                uiOverlayFlow
            ) { snap, sessionState, (defaultLength, creativity), (currentLocalError, dismissedFailed) ->
                if (snap == null) return@combine null
                val visibleNpcs = visibleNpcCards(snap.characters, snap.story, snap.player.name)
                val validRegeneratable = snap.regeneratableTurns intersect snap.narratives.map { it.turn }.toSet()
                val baseMessages = messageItems(snap.narratives, validRegeneratable)
                val isActivePhase = sessionState.phase == RoundPhase.PREPARING ||
                    sessionState.phase == RoundPhase.NARRATING ||
                    sessionState.phase == RoundPhase.GUARDING ||
                    sessionState.phase == RoundPhase.COMMITTING
                val effectiveError = currentLocalError ?: sessionState.errorMessage
                val displayMessages = when {
                    isActivePhase &&
                        sessionState.activeTurn > 0 &&
                        snap.narratives.none { it.turn == sessionState.activeTurn } -> {
                        buildList {
                            addAll(baseMessages)
                            if (sessionState.activeUserInput.isNotBlank()) {
                                add(
                                    ChatMessageItem(
                                        turn = sessionState.activeTurn,
                                        role = "user",
                                        content = sessionState.activeUserInput
                                    )
                                )
                            }
                            add(
                                ChatMessageItem(
                                    turn = sessionState.activeTurn,
                                    role = "assistant",
                                    content = sessionState.streamingText,
                                    isStreaming = true,
                                    canRegenerate = false
                                )
                            )
                        }
                    }
                    !isActivePhase && !sessionState.lastFailedAction.isNullOrBlank() && !effectiveError.isNullOrBlank() -> {
                        baseMessages + ChatMessageItem(
                            turn = snap.story.currentRound + 1,
                            role = "error",
                            content = effectiveError
                        )
                    }
                    else -> baseMessages
                }

                val failedRounds = sessionState.sync.failedRounds
                val showBanner = failedRounds.size >= 3 && !failedRounds.all { it in dismissedFailed }

                ChatUiState(
                    messages = displayMessages,
                    playerState = snap.player,
                    storyState = snap.story,
                    worldSetting = snap.world,
                    npcCards = visibleNpcs,
                    worldbookEntries = snap.worldbook,
                    isReady = true,
                    isStreaming = isActivePhase,
                    canCancelGeneration = isActivePhase && sessionState.canCancelGeneration,
                    currentRound = snap.story.currentRound,
                    statusText = if (isActivePhase) sessionState.statusText else "",
                    errorMessage = effectiveError,
                    lastFailedAction = sessionState.lastFailedAction,
                    regeneratableTurns = validRegeneratable,
                    canSubmitInput = !isActivePhase,
                    syncingRounds = sessionState.sync.syncingRounds,
                    failedSyncRounds = failedRounds,
                    showSyncRetryBanner = showBanner,
                    suggestions = sessionState.suggestions,
                    lastSyncCompletedAtMs = sessionState.sync.lastSyncCompletedAtMs,
                    narrationSettings = snap.narration,
                    narrationCreativity = creativity,
                    defaultNarrationLength = defaultLength
                )
            }.collect { nextState ->
                if (nextState != null) {
                    _uiState.value = nextState
                    if (nextState.failedSyncRounds.isEmpty() && dismissedSyncRounds.value.isNotEmpty()) {
                        dismissedSyncRounds.value = emptySet()
                    }
                    if (
                        !autoOpened &&
                        nextState.messages.isEmpty() &&
                        !nextState.isStreaming &&
                        nextState.errorMessage == null &&
                        nextState.lastFailedAction == null
                    ) {
                        autoOpened = true
                        sendMessage("开始这一天")
                    }
                }
            }
        }
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

    private fun messageItems(
        narratives: List<NarrativeEntry>,
        regeneratableTurns: Set<Int>
    ): List<ChatMessageItem> = narratives.flatMap { narrative ->
        val canRegenerate = narrative.turn in regeneratableTurns
        listOf(
            ChatMessageItem(
                turn = narrative.turn,
                role = "user",
                content = narrative.userInput,
                canRegenerate = canRegenerate
            ),
            ChatMessageItem(
                turn = narrative.turn,
                role = "assistant",
                content = narrative.narrative,
                canRegenerate = canRegenerate
            )
        )
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
}
