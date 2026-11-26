package com.novelchat.ui.screen.chat

import android.content.ClipData
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novelchat.BuildConfig
import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.CharacterFieldDefinition
import com.novelchat.data.model.CharacterFieldPolicy
import com.novelchat.data.model.PlayerState
import com.novelchat.data.model.StoryState
import com.novelchat.ui.theme.AdaptiveSpec
import com.novelchat.ui.theme.AmberContainer
import com.novelchat.ui.theme.CenteredAdaptiveBox
import com.novelchat.ui.theme.rememberAdaptiveSpec
import com.novelchat.ui.theme.AmberPrimary
import com.novelchat.ui.theme.BarNegative
import com.novelchat.ui.theme.BarPositive
import com.novelchat.ui.theme.CvGreen
import com.novelchat.ui.theme.CvOrange
import com.novelchat.ui.theme.CvRed
import com.novelchat.ui.theme.CvYellow
import com.novelchat.ui.theme.EditorialAvatar
import com.novelchat.ui.theme.ErrorContainer
import com.novelchat.ui.theme.Hairline
import com.novelchat.ui.theme.InkMuted
import com.novelchat.ui.theme.InkPrimary
import com.novelchat.ui.theme.InkSecondary
import com.novelchat.ui.theme.MistSecondary
import com.novelchat.ui.theme.NovelFieldShape
import com.novelchat.ui.theme.ObsidianCanvas
import com.novelchat.ui.theme.OnAmberContainer
import com.novelchat.ui.theme.OnAmberPrimary
import com.novelchat.ui.theme.OnErrorContainer
import com.novelchat.ui.theme.OnSageContainer
import com.novelchat.ui.theme.ProseAccentBar
import com.novelchat.ui.theme.SageContainer
import com.novelchat.ui.theme.SageTertiary
import com.novelchat.ui.theme.SurfaceBase
import com.novelchat.ui.theme.SurfaceHigh
import com.novelchat.ui.theme.SurfaceHighest
import com.novelchat.ui.theme.SurfaceLow
import com.novelchat.ui.theme.TonalBadge
import com.novelchat.ui.theme.UserBubbleBg
import com.novelchat.ui.theme.UserBubbleBorder
import com.novelchat.ui.theme.novelTextFieldColors
import com.novelchat.ui.viewmodel.ActionSuggestionPolicy
import com.novelchat.ui.viewmodel.CharacterFieldDisplay
import com.novelchat.ui.viewmodel.CharacterFieldDisplayPolicy
import com.novelchat.ui.viewmodel.ChatHistoryPositionPolicy
import com.novelchat.ui.viewmodel.ChatInputPolicy
import com.novelchat.ui.viewmodel.ChatMessageItem
import com.novelchat.ui.viewmodel.ChatViewModel
import com.novelchat.ui.viewmodel.NpcCardPolicy
import com.novelchat.ui.viewmodel.PlayerCardPolicy
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    characterId: Long,
    onBack: () -> Unit,
    onWorldbook: () -> Unit = {},
    onCharacterList: () -> Unit = {},
    chatViewModel: ChatViewModel = viewModel()
) {
    val state by chatViewModel.uiState.collectAsState()
    var inputText by rememberSaveable { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var selectedNpcName by remember { mutableStateOf<String?>(null) }
    var showPlayerDetail by remember { mutableStateOf(false) }
    var showContextPanel by rememberSaveable { mutableStateOf(true) }
    var contextExpanded by rememberSaveable { mutableStateOf(false) }
    var autoFollowLatest by rememberSaveable(characterId) { mutableStateOf(false) }
    var initialHistoryPositioned by rememberSaveable(characterId) { mutableStateOf(false) }
    var isUserDragging by remember { mutableStateOf(false) }
    var showLeaveConfirmation by remember { mutableStateOf(false) }
    var showSavingNotice by remember { mutableStateOf(false) }
    var showNarrationSettings by remember { mutableStateOf(false) }
    var showPerfDebug by remember { mutableStateOf(false) }
    var perfDebugRefreshTick by remember { mutableStateOf(0) }
    var pendingRegeneration by remember { mutableStateOf<RoundRegenerationRequest?>(null) }
    var editingAction by remember { mutableStateOf<ChatMessageItem?>(null) }
    var editedActionText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0

    val requestBack = {
        when {
            !state.isStreaming -> onBack()
            state.canCancelGeneration -> showLeaveConfirmation = true
            else -> showSavingNotice = true
        }
    }

    BackHandler(enabled = state.isStreaming) { requestBack() }

    LaunchedEffect(characterId) { chatViewModel.initialize(characterId) }

    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> isUserDragging = true
                is DragInteraction.Stop,
                is DragInteraction.Cancel -> {
                    isUserDragging = false
                    autoFollowLatest = !listState.canScrollForward
                }
            }
        }
    }
    LaunchedEffect(listState, isUserDragging) {
        if (isUserDragging) {
            snapshotFlow { listState.canScrollForward }
                .collect { canScrollForward -> autoFollowLatest = !canScrollForward }
        }
    }
    LaunchedEffect(
        state.isReady,
        state.isStreaming,
        state.messages.size,
        initialHistoryPositioned
    ) {
        if (
            state.isReady &&
            !state.isStreaming &&
            !initialHistoryPositioned
        ) {
            ChatHistoryPositionPolicy.initialFocusIndex(state.messages)?.let { targetIndex ->
                withFrameNanos { }
                listState.scrollToItem(targetIndex)
                initialHistoryPositioned = true
                autoFollowLatest = false
            }
        }
    }
    val latestMessageLength = state.messages.lastOrNull()?.content?.length ?: 0
    LaunchedEffect(state.messages.size, latestMessageLength, autoFollowLatest) {
        if (state.messages.isNotEmpty() && autoFollowLatest) {
            withFrameNanos { }
            val lastIndex = state.messages.lastIndex
            if (listState.firstVisibleItemIndex < (lastIndex - 1).coerceAtLeast(0)) {
                listState.scrollToItem(lastIndex)
            }
            listState.scrollBy(1_000_000f)
        }
    }
    LaunchedEffect(state.errorMessage, state.isReady) {
        if (state.errorMessage != null && state.isReady) {
            kotlinx.coroutines.delay(5000)
            chatViewModel.clearError()
        }
    }
    LaunchedEffect(state.isStreaming) {
        if (state.isStreaming) {
            initialHistoryPositioned = true
            autoFollowLatest = true
        } else {
            showLeaveConfirmation = false
            showSavingNotice = false
        }
    }

    val hasMessages = state.messages.isNotEmpty()
    val showJumpToLatest by remember(hasMessages, listState) {
        derivedStateOf { hasMessages && listState.canScrollForward }
    }
    val suggestions = remember(state.suggestions, state.playerState, state.storyState, state.npcCards) {
        if (state.suggestions.isNotEmpty()) {
            state.suggestions
        } else {
            ActionSuggestionPolicy.suggestions(
                player = state.playerState,
                storyState = state.storyState,
                npcCards = state.npcCards
            )
        }
    }
    val canSubmitAction = state.canSubmitInput
    val adaptive = rememberAdaptiveSpec()

    val submitCurrentInput = {
        if (
            inputText.isNotBlank() &&
            state.isReady &&
            canSubmitAction &&
            !state.isStreaming
        ) {
            initialHistoryPositioned = true
            autoFollowLatest = true
            chatViewModel.sendMessage(inputText)
            inputText = ""
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianCanvas)
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.playerState?.name?.ifBlank { "对话" } ?: "对话",
                            color = InkPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subtitle = listOfNotNull(
                            state.playerState?.class_?.takeIf { it.isNotBlank() },
                            state.currentRound.takeIf { it > 0 }?.let { "第${it}轮" }
                        ).joinToString(" · ")
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                color = InkMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = requestBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = InkSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianCanvas),
                actions = {
                    IconButton(
                        onClick = onWorldbook,
                        enabled = !state.isStreaming
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = "世界书",
                            tint = if (state.isStreaming) InkMuted else InkSecondary,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    IconButton(
                        onClick = onCharacterList,
                        enabled = !state.isStreaming
                    ) {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = "角色列表",
                            tint = if (state.isStreaming) InkMuted else InkSecondary,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, "菜单", tint = InkSecondary)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            containerColor = SurfaceHigh
                        ) {
                            DropdownMenuItem(
                                text = { Text("叙事设置", color = InkPrimary) },
                                onClick = { showMenu = false; showNarrationSettings = true },
                                leadingIcon = { Icon(Icons.Default.Settings, null, tint = AmberPrimary) }
                            )
                            DropdownMenuItem(
                                text = { Text("世界书", color = InkPrimary) },
                                onClick = { showMenu = false; onWorldbook() },
                                enabled = !state.isStreaming,
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = InkSecondary) }
                            )
                            DropdownMenuItem(
                                text = { Text("角色列表", color = InkPrimary) },
                                onClick = { showMenu = false; onCharacterList() },
                                enabled = !state.isStreaming,
                                leadingIcon = { Icon(Icons.Default.People, null, tint = InkSecondary) }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (showContextPanel) {
                                            if (adaptive.isTwoPaneChat) "隐藏侧边状态栏" else "隐藏场景状态"
                                        } else {
                                            if (adaptive.isTwoPaneChat) "显示侧边状态栏" else "显示场景状态"
                                        },
                                        color = InkPrimary
                                    )
                                },
                                onClick = { showMenu = false; showContextPanel = !showContextPanel },
                                leadingIcon = {
                                    Icon(
                                        if (showContextPanel) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        null,
                                        tint = InkSecondary
                                    )
                                }
                            )
                            if (BuildConfig.DEBUG || BuildConfig.BUILD_TYPE == "qa") {
                                DropdownMenuItem(
                                    text = { Text("性能调试", color = InkPrimary) },
                                    onClick = {
                                        showMenu = false
                                        perfDebugRefreshTick++
                                        showPerfDebug = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.Info, null, tint = SageTertiary) }
                                )
                            }
                            HorizontalDivider(color = Hairline, modifier = Modifier.padding(vertical = 4.dp))
                            DropdownMenuItem(
                                text = { Text("返回大厅", color = InkSecondary) },
                                onClick = { showMenu = false; requestBack() },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = InkMuted) }
                            )
                        }
                    }
                }
            )

            val readingAndComposerPane: @Composable (Modifier) -> Unit = { paneModifier ->
                Column(modifier = paneModifier) {
                    if (!adaptive.isTwoPaneChat) {
                        state.storyState
                            ?.takeIf { showContextPanel && !isKeyboardVisible }
                            ?.let { storyState ->
                                CenteredAdaptiveBox(maxWidth = adaptive.readingMaxWidth) {
                                    StoryContextCapsule(
                                        storyState = storyState,
                                        player = state.playerState,
                                        npcCards = state.npcCards,
                                        characterFields = state.worldSetting?.characterFields.orEmpty(),
                                        syncingRounds = state.syncingRounds,
                                        failedSyncRounds = state.failedSyncRounds,
                                        expanded = contextExpanded,
                                        onToggleExpanded = {
                                            focusManager.clearFocus(force = true)
                                            contextExpanded = !contextExpanded
                                        },
                                        onRetryStateSync = chatViewModel::retryBackgroundSync,
                                        onPlayerClick = {
                                            focusManager.clearFocus(force = true)
                                            showPlayerDetail = true
                                        },
                                        onNpcClick = {
                                            focusManager.clearFocus(force = true)
                                            selectedNpcName = it.name
                                        }
                                    )
                                }
                            }
                    }

                    AnimatedVisibility(visible = state.isStreaming) {
                        CenteredAdaptiveBox(maxWidth = adaptive.readingMaxWidth) {
                            GenerationStatusStrip(state.statusText)
                        }
                    }

                    // Editorial reading stream
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(if (adaptive.isTablet) 10.dp else 8.dp),
                            contentPadding = PaddingValues(
                                start = if (adaptive.isTablet) 24.dp else 18.dp,
                                top = if (adaptive.isTablet) 14.dp else 10.dp,
                                end = if (adaptive.isTablet) 24.dp else 18.dp,
                                bottom = 56.dp
                            )
                        ) {
                            itemsIndexed(
                                items = state.messages,
                                key = { index, message -> "${message.turn}-${message.role}-$index" }
                            ) { _, message ->
                                CenteredAdaptiveBox(maxWidth = adaptive.readingMaxWidth) {
                                    MessageBubble(
                                        message = message,
                                        playerName = state.playerState?.name.orEmpty().ifBlank { "主角" },
                                        regenerationEnabled = !state.isStreaming,
                                        proseFontSize = adaptive.proseFontSize,
                                        proseLineHeight = adaptive.proseLineHeight,
                                        userBubbleMaxWidth = adaptive.userBubbleMaxWidth,
                                        onCopy = { content ->
                                            scope.launch {
                                                clipboard.setClipEntry(
                                                    ClipEntry(
                                                        ClipData.newPlainText("NovelChat 行动提示词", content)
                                                    )
                                                )
                                                Toast.makeText(
                                                    context,
                                                    "已复制行动提示词",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                        onEdit = { actionMessage ->
                                            editingAction = actionMessage
                                            editedActionText = actionMessage.content
                                        },
                                        onRegenerate = { narrativeMessage ->
                                            state.messages
                                                .lastOrNull {
                                                    it.turn == narrativeMessage.turn && it.role == "user"
                                                }
                                                ?.let { action ->
                                                    pendingRegeneration = RoundRegenerationRequest(
                                                        turn = narrativeMessage.turn,
                                                        action = action.content
                                                    )
                                                }
                                        },
                                        onRetry = {
                                            initialHistoryPositioned = true
                                            autoFollowLatest = true
                                            chatViewModel.retryLastAction()
                                        }
                                    )
                                }
                            }
                        }

                        if (showJumpToLatest) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, ObsidianCanvas.copy(alpha = 0.94f))
                                        )
                                    )
                            )
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 10.dp)
                                    .height(34.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        autoFollowLatest = true
                                        scope.launch {
                                            if (state.messages.isNotEmpty()) {
                                                listState.scrollToItem(state.messages.lastIndex)
                                                listState.scrollBy(1_000_000f)
                                            }
                                        }
                                    },
                                shape = CircleShape,
                                color = SurfaceHigh.copy(alpha = 0.92f),
                                border = BorderStroke(1.dp, Hairline),
                                shadowElevation = 4.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 13.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = "回到最新消息",
                                        tint = InkPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "最新",
                                        color = InkPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    state.errorMessage?.let { error ->
                        CenteredAdaptiveBox(maxWidth = adaptive.readingMaxWidth) {
                            InlineErrorNotice(
                                message = error,
                                onRetry = if (!state.isReady && !state.isStreaming) {
                                    chatViewModel::retryStateRefresh
                                } else {
                                    null
                                }
                            )
                        }
                    }

                    if (state.showSyncRetryBanner) {
                        CenteredAdaptiveBox(maxWidth = adaptive.readingMaxWidth) {
                            SyncRetryBanner(
                                failedRounds = state.failedSyncRounds,
                                onRetry = chatViewModel::retryBackgroundSync,
                                onDismiss = chatViewModel::dismissSyncBanner
                            )
                        }
                    }

                    // Gemini-style floating pill composer (centered on tablets)
                    CenteredAdaptiveBox(maxWidth = adaptive.readingMaxWidth) {
                        InputBar(
                            inputText = inputText,
                            onInputChange = { inputText = ChatInputPolicy.normalizeDraft(it) },
                            suggestions = suggestions,
                            showSuggestions = !isKeyboardVisible,
                            isReady = state.isReady,
                            canSubmit = canSubmitAction,
                            isPreparing = !state.isReady && state.errorMessage == null,
                            isLoading = state.isStreaming,
                            canCancel = state.canCancelGeneration,
                            onSuggestionSelected = { inputText = ChatInputPolicy.normalizeDraft(it) },
                            onSend = submitCurrentInput,
                            onCancel = chatViewModel::cancelStream
                        )
                    }
                }
            }

            if (adaptive.isTwoPaneChat) {
                HorizontalDivider(color = Hairline.copy(alpha = 0.6f))
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AnimatedVisibility(visible = showContextPanel && state.storyState != null) {
                        state.storyState?.let { storyState ->
                            Row(modifier = Modifier.fillMaxHeight()) {
                                TabletStorySidebar(
                                    storyState = storyState,
                                    player = state.playerState,
                                    npcCards = state.npcCards,
                                    characterFields = state.worldSetting?.characterFields.orEmpty(),
                                    syncingRounds = state.syncingRounds,
                                    failedSyncRounds = state.failedSyncRounds,
                                    sidebarWidth = adaptive.sidebarWidth,
                                    streaming = state.isStreaming,
                                    onRetryStateSync = chatViewModel::retryBackgroundSync,
                                    onPlayerClick = {
                                        focusManager.clearFocus(force = true)
                                        showPlayerDetail = true
                                    },
                                    onNpcClick = {
                                        focusManager.clearFocus(force = true)
                                        selectedNpcName = it.name
                                    },
                                    onWorldbook = onWorldbook,
                                    onCharacterList = onCharacterList
                                )
                                VerticalDivider(color = Hairline)
                            }
                        }
                    }
                    readingAndComposerPane(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            } else {
                readingAndComposerPane(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        }
    }

    val selectedCard = selectedNpcName?.let { name ->
        state.npcCards.find { it.name == name }
    }
    selectedCard?.let { card ->
        NpcDetailSheet(
            card = card,
            playerName = state.playerState?.name.orEmpty().ifBlank { "主角" },
            characterFields = state.worldSetting?.characterFields.orEmpty(),
            onDismiss = { selectedNpcName = null }
        )
    }
    if (showPlayerDetail) {
        state.playerState?.let { player ->
            PlayerDetailSheet(
                player = player,
                pendingEvents = state.storyState?.pendingEvents.orEmpty(),
                characterFields = state.worldSetting?.characterFields.orEmpty(),
                onDismiss = { showPlayerDetail = false }
            )
        }
    }
    if (showLeaveConfirmation) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirmation = false },
            title = { Text("停止生成并返回？", color = InkPrimary, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    text = "本轮剧情仍在生成，停止后未写入的正文不会保留；已完成轮次的后台同步会继续执行。",
                    color = InkSecondary,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLeaveConfirmation = false
                        chatViewModel.cancelStream()
                        onBack()
                    }
                ) {
                    Text("停止并返回", color = CvRed, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirmation = false }) {
                    Text("继续等待", color = InkSecondary)
                }
            },
            containerColor = SurfaceHigh,
            shape = RoundedCornerShape(28.dp)
        )
    }

    if (showNarrationSettings) {
        NarrationSettingsDialog(
            settings = state.narrationSettings,
            defaultLength = state.defaultNarrationLength,
            creativity = state.narrationCreativity,
            onSave = { updated, creativity ->
                showNarrationSettings = false
                chatViewModel.updateNarrationSettings(updated, creativity)
            },
            onDismiss = { showNarrationSettings = false }
        )
    }

    if (showPerfDebug) {
        val perfSnapshot = remember(perfDebugRefreshTick) {
            chatViewModel.loadPerfDebugSnapshot()
        }
        PerfDebugSheet(
            snapshot = perfSnapshot,
            onRefresh = { perfDebugRefreshTick++ },
            onDismiss = { showPerfDebug = false }
        )
    }
    if (showSavingNotice) {
        AlertDialog(
            onDismissRequest = { showSavingNotice = false },
            title = { Text("正在保存本轮状态", color = InkPrimary, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    text = "剧情已经生成完成，角色与任务状态正在写入。完成后即可安全返回。",
                    color = InkSecondary,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showSavingNotice = false }) {
                    Text("知道了", color = AmberPrimary, fontWeight = FontWeight.SemiBold)
                }
            },
            containerColor = SurfaceHigh,
            shape = RoundedCornerShape(28.dp)
        )
    }
    pendingRegeneration?.let { request ->
        AlertDialog(
            onDismissRequest = { pendingRegeneration = null },
            icon = { Icon(Icons.Default.Refresh, null, tint = AmberPrimary) },
            title = { Text("重新生成第${request.turn}轮？", color = InkPrimary, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    text = "将回到第${request.turn}轮开始前，并替换这一轮及之后的旧剧情、角色和世界状态。",
                    color = InkSecondary,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRegeneration = null
                        initialHistoryPositioned = true
                        autoFollowLatest = true
                        chatViewModel.regenerateRound(request.turn, request.action)
                    }
                ) {
                    Text("重新生成", color = AmberPrimary, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRegeneration = null }) {
                    Text("取消", color = InkSecondary)
                }
            },
            containerColor = SurfaceHigh,
            shape = RoundedCornerShape(28.dp)
        )
    }
    editingAction?.let { message ->
        AlertDialog(
            onDismissRequest = { editingAction = null },
            icon = { Icon(Icons.Default.Edit, null, tint = AmberPrimary) },
            title = { Text("修改第${message.turn}轮行动", color = InkPrimary, fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = editedActionText,
                        onValueChange = {
                            editedActionText = ChatInputPolicy.normalizeDraft(it)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 110.dp, max = 240.dp),
                        placeholder = { Text("描述新的行动...") },
                        maxLines = 10,
                        shape = NovelFieldShape,
                        colors = novelTextFieldColors(SurfaceBase)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "保存后会回到本轮开始前，并根据修改后的行动重新生成；本轮之后的旧分支将被替换。",
                        color = InkSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = editedActionText.isNotBlank(),
                    onClick = {
                        val revised = editedActionText
                        editingAction = null
                        initialHistoryPositioned = true
                        autoFollowLatest = true
                        chatViewModel.regenerateRound(message.turn, revised)
                    }
                ) {
                    Text("保存并重新生成", color = AmberPrimary, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingAction = null }) {
                    Text("取消", color = InkSecondary)
                }
            },
            containerColor = SurfaceHigh,
            shape = RoundedCornerShape(28.dp)
        )
    }
}

@Composable
private fun InlineErrorNotice(message: String, onRetry: (() -> Unit)? = null) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = ErrorContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = CvRed,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = message,
                color = OnErrorContainer,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.weight(1f)
            )
            if (onRetry != null) {
                TextButton(onClick = onRetry) {
                    Text("重试", color = AmberPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SyncRetryBanner(
    failedRounds: Set<Int>,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val roundsLabel = failedRounds.sorted().joinToString("、")
    Surface(
        color = AmberContainer,
        border = BorderStroke(1.dp, AmberPrimary.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "第 $roundsLabel 轮后台状态同步失败，可继续游玩或重试",
                color = OnAmberContainer,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onRetry,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("重试", color = AmberPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "隐藏同步提示",
                    tint = OnAmberContainer.copy(alpha = 0.7f),
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
private fun StoryContextCapsule(
    storyState: StoryState,
    player: PlayerState?,
    npcCards: List<CharacterCard>,
    characterFields: List<CharacterFieldDefinition>,
    syncingRounds: Set<Int> = emptySet(),
    failedSyncRounds: Set<Int> = emptySet(),
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onRetryStateSync: () -> Unit,
    onPlayerClick: () -> Unit,
    onNpcClick: (CharacterCard) -> Unit
) {
    val sceneTitle = storyState.currentLocation.ifBlank { "当前场景" }
    val sceneMeta = listOf(storyState.timeOfDay, storyState.weather, storyState.mood)
        .filter { it.isNotBlank() }
        .joinToString(" · ")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        color = SurfaceLow,
        border = BorderStroke(1.dp, Hairline)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Scene header strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(AmberContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = AmberPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sceneTitle,
                        color = InkPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (sceneMeta.isNotBlank()) {
                        Text(
                            text = sceneMeta,
                            color = InkMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (syncingRounds.isNotEmpty()) {
                    Surface(
                        shape = CircleShape,
                        color = SageContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                strokeWidth = 1.5.dp,
                                color = SageTertiary
                            )
                            Text(
                                text = "同步${syncingRounds.sorted().joinToString("/")}",
                                color = OnSageContainer,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                } else if (failedSyncRounds.isNotEmpty()) {
                    TonalBadge(
                        text = "补同步(${failedSyncRounds.size})",
                        containerColor = AmberContainer,
                        contentColor = OnAmberContainer,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onRetryStateSync)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                if (npcCards.isNotEmpty()) {
                    TonalBadge(
                        text = "${npcCards.size} 人在场",
                        containerColor = SurfaceBase,
                        contentColor = SageTertiary
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "收起场景状态" else "展开场景状态",
                    tint = InkMuted,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp)
                )
            }

            // Compact protagonist status row inside same capsule
            player?.let { currentPlayer ->
                HorizontalDivider(
                    color = Hairline,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
                CompactPlayerRow(
                    player = currentPlayer,
                    characterFields = characterFields,
                    onClick = onPlayerClick
                )
            }

            // Expandable Goal + Active NPCs drawer
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val goal = player?.let {
                        PlayerCardPolicy.currentGoal(it, storyState.pendingEvents)
                    }.orEmpty()
                    if (goal.isNotBlank()) {
                        GoalRow(goal)
                    }
                    if (npcCards.isNotEmpty()) {
                        ActiveCharacterRow(
                            cards = npcCards,
                            playerName = player?.name.orEmpty().ifBlank { "主角" },
                            characterFields = characterFields,
                            onNpcClick = onNpcClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactPlayerRow(
    player: PlayerState,
    characterFields: List<CharacterFieldDefinition>,
    onClick: () -> Unit
) {
    val name = player.name.ifBlank { "林克" }
    val role = player.class_.ifBlank { "城市居民" }
    val primaryFields = CharacterFieldDisplayPolicy.playerPrimary(player, characterFields)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EditorialAvatar(name = name, size = 30.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = InkPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = role,
                color = InkMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            primaryFields.forEach { field ->
                StatusPillChip(
                    label = field.definition.label,
                    value = compactFieldValue(field),
                    valueColor = characterFieldColor(field)
                )
            }
        }
    }
}

@Composable
private fun StatusPillChip(label: String, value: String, valueColor: Color) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = SurfaceBase
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                color = InkMuted,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                color = valueColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun GoalRow(goal: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = SurfaceBase
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Flag,
                contentDescription = null,
                tint = AmberPrimary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = goal,
                color = InkPrimary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ActiveCharacterRow(
    cards: List<CharacterCard>,
    playerName: String,
    characterFields: List<CharacterFieldDefinition>,
    onNpcClick: (CharacterCard) -> Unit
) {
    Column(modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("场上角色", color = InkMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(6.dp))
            Text("${cards.size} 人", color = InkMuted, fontSize = 11.sp)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            cards.forEach { card ->
                NpcSceneCard(
                    card = card,
                    playerName = playerName,
                    characterFields = characterFields,
                    onClick = { onNpcClick(card) }
                )
            }
        }
    }
}

@Composable
private fun TabletStorySidebar(
    storyState: StoryState,
    player: PlayerState?,
    npcCards: List<CharacterCard>,
    characterFields: List<CharacterFieldDefinition>,
    syncingRounds: Set<Int>,
    failedSyncRounds: Set<Int>,
    sidebarWidth: Dp,
    streaming: Boolean,
    onRetryStateSync: () -> Unit,
    onPlayerClick: () -> Unit,
    onNpcClick: (CharacterCard) -> Unit,
    onWorldbook: () -> Unit,
    onCharacterList: () -> Unit
) {
    val sceneTitle = storyState.currentLocation.ifBlank { "当前场景" }
    val sceneMeta = listOf(storyState.timeOfDay, storyState.weather, storyState.mood)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
    val goal = player?.let { PlayerCardPolicy.currentGoal(it, storyState.pendingEvents) }.orEmpty()

    Column(
        modifier = Modifier
            .width(sidebarWidth)
            .fillMaxHeight()
            .background(SurfaceLow)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Scene card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = SurfaceBase,
                border = BorderStroke(1.dp, Hairline)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(AmberContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = null,
                                tint = AmberPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = sceneTitle,
                            color = InkPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (sceneMeta.isNotBlank()) {
                        Text(
                            text = sceneMeta,
                            color = InkSecondary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                    if (syncingRounds.isNotEmpty() || failedSyncRounds.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (syncingRounds.isNotEmpty()) {
                                Surface(shape = CircleShape, color = SageContainer) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(10.dp),
                                            strokeWidth = 1.5.dp,
                                            color = SageTertiary
                                        )
                                        Text(
                                            text = "同步第${syncingRounds.sorted().joinToString("/")}轮",
                                            color = OnSageContainer,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            } else if (failedSyncRounds.isNotEmpty()) {
                                TonalBadge(
                                    text = "补同步 (${failedSyncRounds.size})",
                                    containerColor = AmberContainer,
                                    contentColor = OnAmberContainer,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable(onClick = onRetryStateSync)
                                )
                            }
                        }
                    }
                }
            }

            // Protagonist card
            player?.let { currentPlayer ->
                val primaryFields = CharacterFieldDisplayPolicy.playerPrimary(currentPlayer, characterFields)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onPlayerClick),
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceBase,
                    border = BorderStroke(1.dp, Hairline)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            EditorialAvatar(name = currentPlayer.name.ifBlank { "主角" }, size = 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentPlayer.name.ifBlank { "林克" },
                                    color = InkPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = currentPlayer.class_.ifBlank { "城市居民" },
                                    color = InkMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "查看主角详情",
                                tint = InkMuted.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        if (primaryFields.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                primaryFields.forEach { field ->
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        color = SurfaceLow
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(field.definition.label, color = InkMuted, fontSize = 10.sp, maxLines = 1)
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = compactFieldValue(field),
                                                color = characterFieldColor(field),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (goal.isNotBlank()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = SurfaceLow
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        Icons.Default.Flag,
                                        contentDescription = null,
                                        tint = AmberPrimary,
                                        modifier = Modifier.padding(top = 2.dp).size(13.dp)
                                    )
                                    Spacer(Modifier.width(7.dp))
                                    Text(
                                        text = goal,
                                        color = InkPrimary,
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Present NPCs unified editorial roster
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "在场人物",
                        color = InkSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${npcCards.size} 人",
                        color = if (npcCards.isNotEmpty()) SageTertiary else InkMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceBase,
                    border = BorderStroke(1.dp, Hairline)
                ) {
                    if (npcCards.isEmpty()) {
                        Text(
                            text = "当前场景暂无其他在场人物",
                            color = InkMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            npcCards.forEachIndexed { index, card ->
                                SidebarNpcRow(
                                    card = card,
                                    playerName = player?.name.orEmpty().ifBlank { "主角" },
                                    characterFields = characterFields,
                                    onClick = { onNpcClick(card) }
                                )
                                if (index < npcCards.lastIndex) {
                                    HorizontalDivider(
                                        color = Hairline,
                                        modifier = Modifier.padding(start = 62.dp, end = 12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Pinned sidebar footer actions
        HorizontalDivider(color = Hairline.copy(alpha = 0.7f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                onClick = onWorldbook,
                enabled = !streaming,
                modifier = Modifier.weight(1f),
                shape = CircleShape,
                color = SurfaceBase,
                border = BorderStroke(1.dp, Hairline)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = if (streaming) InkMuted else AmberPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "世界书",
                        color = if (streaming) InkMuted else InkPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Surface(
                onClick = onCharacterList,
                enabled = !streaming,
                modifier = Modifier.weight(1f),
                shape = CircleShape,
                color = SurfaceBase,
                border = BorderStroke(1.dp, Hairline)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.People,
                        contentDescription = null,
                        tint = if (streaming) InkMuted else SageTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "全部角色",
                        color = if (streaming) InkMuted else InkPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarNpcRow(
    card: CharacterCard,
    playerName: String,
    characterFields: List<CharacterFieldDefinition>,
    onClick: () -> Unit
) {
    val relationship = NpcCardPolicy.relationshipSnapshot(card, playerName)
    val primaryFields = CharacterFieldDisplayPolicy.npcPrimary(
        card,
        playerName,
        characterFields
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EditorialAvatar(name = card.name, size = 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = card.name,
                    color = InkPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = SurfaceHigh
                ) {
                    Text(
                        text = relationship.stage,
                        color = SageTertiary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
            Text(
                text = card.occupation.ifBlank { "身份未知" },
                color = InkMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "好感",
                        color = InkMuted,
                        fontSize = 11.sp
                    )
                    Text(
                        text = formatFavor(relationship.favor),
                        color = favorColor(relationship.favor),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
                Text(
                    text = "·",
                    color = InkMuted.copy(alpha = 0.45f),
                    fontSize = 11.sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "信任",
                        color = InkMuted,
                        fontSize = 11.sp
                    )
                    Text(
                        text = NpcCardPolicy.trustLabel(relationship.trust),
                        color = trustColor(relationship.trust),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
            if (primaryFields.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    primaryFields.forEach { field ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SurfaceLow
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(field.definition.label, color = InkMuted, fontSize = 10.sp, maxLines = 1)
                                Text(
                                    text = compactFieldValue(field),
                                    color = characterFieldColor(field),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "查看${card.name}详情",
            tint = InkMuted.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun NpcSceneCard(
    card: CharacterCard,
    playerName: String,
    characterFields: List<CharacterFieldDefinition>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.width(176.dp)
) {
    val relationship = NpcCardPolicy.relationshipSnapshot(card, playerName)

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceBase,
        border = BorderStroke(1.dp, Hairline)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EditorialAvatar(name = card.name, size = 32.dp)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = card.name,
                        color = InkPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = card.occupation.ifBlank { "身份未知" },
                        color = InkMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            val primaryFields = CharacterFieldDisplayPolicy.npcPrimary(
                card,
                playerName,
                characterFields
            )
            if (primaryFields.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    primaryFields.forEach { field ->
                        StatusPillChip(
                            label = field.definition.label,
                            value = compactFieldValue(field),
                            valueColor = characterFieldColor(field)
                        )
                    }
                }
            }

            HorizontalDivider(color = Hairline)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TonalBadge(
                    text = relationship.stage,
                    containerColor = SurfaceHigh,
                    contentColor = SageTertiary,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "好感 ${formatFavor(relationship.favor)}",
                        color = favorColor(relationship.favor),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        text = "信任 ${NpcCardPolicy.trustLabel(relationship.trust)}",
                        color = trustColor(relationship.trust),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun GenerationStatusStrip(statusText: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.8.dp,
                color = AmberPrimary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = statusText.ifBlank { "正在续写剧情..." },
                color = InkSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(CircleShape),
            color = AmberPrimary,
            trackColor = SurfaceHigh
        )
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessageItem,
    playerName: String,
    regenerationEnabled: Boolean,
    proseFontSize: TextUnit = 16.sp,
    proseLineHeight: TextUnit = 27.sp,
    userBubbleMaxWidth: Dp = 310.dp,
    onCopy: (String) -> Unit,
    onEdit: (ChatMessageItem) -> Unit,
    onRegenerate: (ChatMessageItem) -> Unit,
    onRetry: () -> Unit
) {
    when (message.role) {
        "user" -> UserActionBubble(
            message = message,
            playerName = playerName,
            regenerationEnabled = regenerationEnabled,
            maxWidth = userBubbleMaxWidth,
            onCopy = onCopy,
            onEdit = onEdit
        )
        "error" -> ErrorMessageBubble(message, onRetry)
        else -> NarrativeMessage(
            message = message,
            regenerationEnabled = regenerationEnabled,
            proseFontSize = proseFontSize,
            proseLineHeight = proseLineHeight,
            onRegenerate = onRegenerate
        )
    }
}

@Composable
private fun UserActionBubble(
    message: ChatMessageItem,
    playerName: String,
    regenerationEnabled: Boolean,
    maxWidth: Dp = 310.dp,
    onCopy: (String) -> Unit,
    onEdit: (ChatMessageItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        horizontalAlignment = Alignment.End
    ) {
        Text(
            text = "$playerName · 第${message.turn}轮",
            color = InkMuted,
            fontSize = 10.sp,
            modifier = Modifier.padding(end = 6.dp, bottom = 4.dp)
        )
        Surface(
            color = UserBubbleBg,
            border = BorderStroke(1.dp, UserBubbleBorder),
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 6.dp,
                bottomStart = 20.dp,
                bottomEnd = 20.dp
            ),
            modifier = Modifier.widthIn(max = maxWidth)
        ) {
            Text(
                text = message.content,
                color = InkPrimary,
                fontSize = 14.5.sp,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp)
            )
        }
        Row(
            modifier = Modifier.padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            TextButton(
                onClick = { onCopy(message.content) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Icon(Icons.Default.ContentCopy, null, tint = InkMuted, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text("复制", color = InkMuted, fontSize = 11.sp)
            }
            if (regenerationEnabled) {
                TextButton(
                    onClick = { onEdit(message) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Edit, null, tint = InkMuted, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("修改", color = InkMuted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun NarrativeMessage(
    message: ChatMessageItem,
    regenerationEnabled: Boolean,
    proseFontSize: TextUnit = 16.sp,
    proseLineHeight: TextUnit = 27.sp,
    onRegenerate: (ChatMessageItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 14.dp)
    ) {
        // Editorial turn header divider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TonalBadge(
                text = "第 ${message.turn} 轮",
                containerColor = SurfaceLow,
                contentColor = InkMuted
            )
            Spacer(Modifier.width(10.dp))
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = Hairline.copy(alpha = 0.7f)
            )
            if (message.isStreaming) {
                Spacer(Modifier.width(10.dp))
                Text("续写中", color = AmberPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            } else if (regenerationEnabled) {
                Spacer(Modifier.width(6.dp))
                TextButton(
                    onClick = { onRegenerate(message) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Refresh, null, tint = InkMuted, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("重新生成", color = InkMuted, fontSize = 11.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Editorial prose column
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .width(2.dp)
                    .height(28.dp)
                    .clip(CircleShape)
                    .background(ProseAccentBar)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message.content,
                    color = InkPrimary,
                    fontSize = proseFontSize,
                    lineHeight = proseLineHeight,
                    letterSpacing = 0.2.sp
                )
                if (message.isStreaming) {
                    Text(
                        text = "▍",
                        color = AmberPrimary,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

private data class RoundRegenerationRequest(
    val turn: Int,
    val action: String
)

@Composable
private fun ErrorMessageBubble(message: ChatMessageItem, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        color = ErrorContainer,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.ErrorOutline, null, tint = CvRed, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = message.content,
                color = OnErrorContainer,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, null, tint = AmberPrimary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("重试", color = AmberPrimary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun InputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    suggestions: List<String>,
    showSuggestions: Boolean,
    isReady: Boolean,
    canSubmit: Boolean,
    isPreparing: Boolean,
    isLoading: Boolean,
    canCancel: Boolean,
    onSuggestionSelected: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianCanvas)
            .navigationBarsPadding()
            .padding(bottom = 8.dp)
    ) {
        // Airy horizontal suggestion pills
        AnimatedVisibility(visible = showSuggestions && inputText.isBlank() && suggestions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestions.forEach { suggestion ->
                    Surface(
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .clip(CircleShape)
                            .clickable { onSuggestionSelected(suggestion) },
                        color = SurfaceLow,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, Hairline)
                    ) {
                        Box(
                            modifier = Modifier
                                .heightIn(min = 36.dp)
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = suggestion,
                                color = InkSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Gemini-inspired unified pill composer
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            shape = RoundedCornerShape(26.dp),
            color = SurfaceHigh,
            border = BorderStroke(1.dp, Hairline)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp, max = 124.dp)
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (inputText.isEmpty()) {
                        Text(
                            text = when {
                                !isReady -> "正在加载故事..."
                                !canSubmit -> "等待本轮完成..."
                                else -> "描述你的下一步行动..."
                            },
                            color = InkMuted,
                            fontSize = 15.sp
                        )
                    }
                    BasicTextField(
                        value = inputText,
                        onValueChange = onInputChange,
                        enabled = isReady,
                        textStyle = TextStyle(
                            color = InkPrimary,
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        ),
                        cursorBrush = SolidColor(AmberPrimary),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (isReady && canSubmit && !isLoading && inputText.isNotBlank()) onSend()
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onPreviewKeyEvent { keyEvent ->
                                val isEnter = keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter
                                if (
                                    keyEvent.type == KeyEventType.KeyDown &&
                                    isEnter &&
                                    (!keyEvent.isShiftPressed || keyEvent.isCtrlPressed || keyEvent.isMetaPressed)
                                ) {
                                    if (isReady && canSubmit && !isLoading && inputText.isNotBlank()) {
                                        onSend()
                                    }
                                    true
                                } else {
                                    false
                                }
                            }
                    )
                }

                Spacer(Modifier.width(8.dp))

                val sendEnabled = isReady && if (isLoading) {
                    canCancel
                } else {
                    canSubmit && inputText.isNotBlank()
                }
                Surface(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(
                            enabled = sendEnabled,
                            onClick = if (isLoading) onCancel else onSend
                        ),
                    shape = CircleShape,
                    color = when {
                        !isReady -> SurfaceHighest
                        isLoading && canCancel -> ErrorContainer
                        isLoading -> SurfaceHighest
                        canSubmit && inputText.isNotBlank() -> AmberPrimary
                        else -> SurfaceHighest
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        when {
                            isPreparing -> CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = InkMuted
                            )
                            isLoading && canCancel -> Icon(
                                Icons.Default.Stop,
                                contentDescription = "停止生成",
                                tint = CvRed,
                                modifier = Modifier.size(18.dp)
                            )
                            isLoading -> CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = InkMuted
                            )
                            else -> Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "发送",
                                tint = if (isReady && canSubmit && inputText.isNotBlank()) {
                                    OnAmberPrimary
                                } else {
                                    InkMuted
                                },
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerDetailSheet(
    player: PlayerState,
    pendingEvents: List<String>,
    characterFields: List<CharacterFieldDefinition>,
    onDismiss: () -> Unit
) {
    val goal = PlayerCardPolicy.currentGoal(player, pendingEvents)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceBase,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(max = 680.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DetailSheetHeader(
                title = player.name.ifBlank { "林克" },
                subtitle = player.class_.ifBlank { "城市居民" },
                onDismiss = onDismiss
            )

            CharacterFieldSections(
                CharacterFieldDisplayPolicy.playerDetails(player, characterFields)
            )

            val activeQuests = player.questLog.filterNot { it.status.contains("完成") || it.status.contains("失败") }
            val otherPendingEvents = pendingEvents.filterNot { it.trim() == goal.trim() }
            if (goal.isNotBlank() || activeQuests.isNotEmpty() || otherPendingEvents.isNotEmpty()) {
                DetailGroupCard("目标与待办") {
                    goal.takeIf { it.isNotBlank() }?.let { DetailTextBlock("当前目标", it) }
                    activeQuests.forEach { quest ->
                        DetailTextBlock(
                            quest.name.ifBlank { "进行中" },
                            quest.currentStep.ifBlank { quest.description.ifBlank { quest.status } }
                        )
                    }
                    otherPendingEvents.forEachIndexed { index, event ->
                        DetailTextBlock("待办 ${index + 1}", event)
                    }
                }
            }

            if (player.inventory.isNotEmpty()) {
                DetailGroupCard("随身物品") {
                    player.inventory.forEach { item ->
                        DetailRow(item.name.ifBlank { "未命名物品" }, "×${item.quantity.coerceAtLeast(1)}", SageTertiary)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NpcDetailSheet(
    card: CharacterCard,
    playerName: String,
    characterFields: List<CharacterFieldDefinition>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceBase,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(max = 680.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DetailSheetHeader(
                title = card.name,
                subtitle = card.occupation.ifBlank { "身份未知" },
                onDismiss = onDismiss
            )

            CharacterFieldSections(
                CharacterFieldDisplayPolicy.npcDetails(card, playerName, characterFields)
            )

            if (card.inventory.isNotEmpty()) {
                DetailGroupCard("持有物品") {
                    card.inventory.forEach { item ->
                        DetailRow(item.name.ifBlank { "未命名物品" }, "×${item.quantity.coerceAtLeast(1)}", SageTertiary)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSheetHeader(title: String, subtitle: String, onDismiss: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        EditorialAvatar(name = title, size = 48.dp, shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = InkPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 19.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = InkSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, "关闭", tint = InkSecondary)
        }
    }
}

@Composable
private fun DetailGroupCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = SurfaceLow,
        border = BorderStroke(1.dp, Hairline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                color = AmberPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 0.4.sp
            )
            content()
        }
    }
}

@Composable
private fun CharacterFieldSections(fields: List<CharacterFieldDisplay>) {
    val groupOrder = listOf(
        CharacterFieldDefinition.GROUP_STATUS,
        CharacterFieldDefinition.GROUP_RELATIONSHIP,
        CharacterFieldDefinition.GROUP_ABILITY,
        CharacterFieldDefinition.GROUP_PROFILE,
        CharacterFieldDefinition.GROUP_IDENTITY
    )
    val grouped = fields.groupBy { it.definition.group }
    groupOrder.forEach { group ->
        val groupFields = grouped[group].orEmpty()
        if (groupFields.isEmpty()) return@forEach
        DetailGroupCard(characterFieldGroupTitle(group)) {
            groupFields.forEach { field ->
                val useBlockLayout = field.definition.valueType in setOf(
                    CharacterFieldDefinition.TYPE_TEXT,
                    CharacterFieldDefinition.TYPE_LIST
                ) && (
                    field.rawValue.length > 18 ||
                        field.definition.key in dialogBlockFieldKeys
                    )
                if (useBlockLayout) {
                    DetailTextBlock(field.definition.label, field.rawValue)
                } else {
                    DetailRow(field.definition.label, field.value, characterFieldColor(field))
                }
            }
        }
    }
}

private val dialogBlockFieldKeys = setOf(
    "appearance",
    "personality",
    "background",
    "current_thoughts",
    "skills"
)

private val higherIsWorseKeywords = listOf(
    "风险", "警觉", "危险", "警戒", "威胁", "暴露", "通缉", "怀疑", "压力", "疲劳", "污染", "腐化"
)

private fun characterFieldGroupTitle(group: String): String = when (group) {
    CharacterFieldDefinition.GROUP_IDENTITY -> "基本信息"
    CharacterFieldDefinition.GROUP_PROFILE -> "人物档案"
    CharacterFieldDefinition.GROUP_ABILITY -> "能力与成长"
    CharacterFieldDefinition.GROUP_RELATIONSHIP -> "关系"
    else -> "当前状态"
}

private fun compactFieldValue(field: CharacterFieldDisplay): String {
    return CharacterFieldPolicy.formatValue(field.definition, field.rawValue, compact = true)
}

private fun characterFieldColor(field: CharacterFieldDisplay): Color {
    val def = field.definition
    if (def.valueType != CharacterFieldDefinition.TYPE_NUMBER) {
        return when (def.key) {
            "health" -> healthColor(field.rawValue)
            "fatigue" -> fatigueColor(field.rawValue)
            "stress" -> stressColor(field.rawValue)
            "pain" -> if (field.rawValue == "无") BarPositive else CvYellow
            else -> when (def.group) {
                CharacterFieldDefinition.GROUP_RELATIONSHIP -> SageTertiary
                CharacterFieldDefinition.GROUP_STATUS -> MistSecondary
                CharacterFieldDefinition.GROUP_ABILITY -> BarPositive
                else -> InkPrimary
            }
        }
    }
    val value = field.rawValue.toDoubleOrNull() ?: return InkPrimary
    return when {
        def.key == "favor" -> favorColor(value.toInt())
        def.key == "trust" -> trustColor(value.toInt())
        def.key == "age" || def.group == CharacterFieldDefinition.GROUP_IDENTITY -> InkPrimary
        def.key == "level" -> SageTertiary
        def.group == CharacterFieldDefinition.GROUP_ABILITY -> BarPositive
        def.key == "risk" || higherIsWorseKeywords.any { def.label.contains(it) } -> {
            val range = (def.maxValue - def.minValue).toDouble()
            val normalized = if (range > 0.0) {
                (((value - def.minValue) / range).coerceIn(0.0, 1.0) * 100.0).toInt()
            } else {
                value.toInt()
            }
            riskColor(normalized)
        }
        else -> {
            val range = (def.maxValue - def.minValue).toDouble()
            if (range <= 0.0) return InkPrimary
            val ratio = ((value - def.minValue) / range).coerceIn(0.0, 1.0)
            when {
                ratio <= 0.2 -> CvRed
                ratio <= 0.45 -> CvYellow
                else -> SageTertiary
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = InkSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(
            text = value.ifBlank { "未知" },
            color = valueColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.2f)
        )
    }
}

@Composable
private fun DetailTextBlock(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = InkMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Text(value, color = InkPrimary, fontSize = 13.sp, lineHeight = 20.sp)
    }
}

private fun formatFavor(favor: Int): String = if (favor >= 0) "+$favor" else favor.toString()

private fun favorColor(value: Int): Color = when {
    value > 0 -> BarPositive
    value < 0 -> BarNegative
    else -> InkSecondary
}

private fun trustColor(value: Int?): Color = when {
    value == null -> InkSecondary
    value >= 70 -> SageTertiary
    value >= 35 -> BarPositive
    else -> CvYellow
}

private fun healthColor(value: String): Color = when {
    value.contains("濒死") || value.contains("重伤") -> CvRed
    value.contains("轻伤") || value.contains("不适") -> CvYellow
    else -> BarPositive
}

private fun fatigueColor(value: String): Color = when {
    value.contains("重度") -> CvRed
    value.contains("中度") -> CvOrange
    value.contains("轻度") -> CvYellow
    else -> BarPositive
}

private fun riskColor(value: Int): Color = when {
    value >= 80 -> CvRed
    value >= 50 -> CvOrange
    value >= 20 -> CvYellow
    else -> CvGreen
}

private fun stressColor(value: String): Color = when {
    value.contains("高") || value.contains("紧张") || value.contains("崩") -> CvRed
    value.contains("中") || value.contains("焦") || value.contains("累") -> CvYellow
    else -> BarPositive
}
