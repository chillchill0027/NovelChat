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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.CharacterFieldDefinition
import com.novelchat.data.model.CharacterFieldPolicy
import com.novelchat.data.model.PlayerState
import com.novelchat.data.model.StoryState
import com.novelchat.domain.engine.StateSyncPolicy
import com.novelchat.ui.theme.BarNegative
import com.novelchat.ui.theme.BarPositive
import com.novelchat.ui.theme.BrightText
import com.novelchat.ui.theme.ContextBg
import com.novelchat.ui.theme.CvGreen
import com.novelchat.ui.theme.CvOrange
import com.novelchat.ui.theme.CvRed
import com.novelchat.ui.theme.CvYellow
import com.novelchat.ui.theme.DarkBg
import com.novelchat.ui.theme.DimText
import com.novelchat.ui.theme.NeonCyan
import com.novelchat.ui.theme.OutlineSoft
import com.novelchat.ui.theme.PanelBg
import com.novelchat.ui.theme.PrimaryPink
import com.novelchat.ui.theme.PrimaryPurple
import com.novelchat.ui.theme.SurfaceLight
import com.novelchat.ui.theme.UserActionBg
import com.novelchat.ui.viewmodel.ActionSuggestionPolicy
import com.novelchat.ui.viewmodel.ChatHistoryPositionPolicy
import com.novelchat.ui.viewmodel.ChatMessageItem
import com.novelchat.ui.viewmodel.ChatInputPolicy
import com.novelchat.ui.viewmodel.ChatViewModel
import com.novelchat.ui.viewmodel.CharacterFieldDisplay
import com.novelchat.ui.viewmodel.CharacterFieldDisplayPolicy
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
    val suggestions = remember(state.playerState, state.storyState, state.npcCards) {
        ActionSuggestionPolicy.suggestions(
            player = state.playerState,
            storyState = state.storyState,
            npcCards = state.npcCards
        )
    }
    val canSubmitAction = state.storyState?.let(StateSyncPolicy::pendingFromRound) == null

    Box(modifier = Modifier.fillMaxSize().background(DarkBg).imePadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.playerState?.name?.ifBlank { "对话" } ?: "对话",
                            color = PrimaryPink,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        val subtitle = listOfNotNull(
                            state.playerState?.class_?.takeIf { it.isNotBlank() },
                            state.storyState?.currentLocation?.takeIf { it.isNotBlank() },
                            state.currentRound.takeIf { it > 0 }?.let { "第${it}轮" }
                        ).joinToString(" · ")
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                color = DimText,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = requestBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = DimText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg),
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.Menu, "菜单", tint = DimText)
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("世界书") },
                                onClick = { showMenu = false; onWorldbook() },
                                enabled = !state.isStreaming,
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("角色列表") },
                                onClick = { showMenu = false; onCharacterList() },
                                enabled = !state.isStreaming,
                                leadingIcon = { Icon(Icons.Default.People, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (showContextPanel) "隐藏场景状态" else "显示场景状态") },
                                onClick = { showMenu = false; showContextPanel = !showContextPanel },
                                leadingIcon = {
                                    Icon(
                                        if (showContextPanel) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        null
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("返回大厅") },
                                onClick = { showMenu = false; requestBack() },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) }
                            )
                        }
                    }
                }
            )

            state.storyState
                ?.takeIf { showContextPanel && !isKeyboardVisible }
                ?.let { storyState ->
                StoryContextPanel(
                    storyState = storyState,
                    player = state.playerState,
                    npcCards = state.npcCards,
                    characterFields = state.worldSetting?.characterFields.orEmpty(),
                    isStateSyncing = state.isStreaming && !state.canCancelGeneration,
                    expanded = contextExpanded,
                    onToggleExpanded = {
                        focusManager.clearFocus(force = true)
                        contextExpanded = !contextExpanded
                    },
                    onRetryStateSync = chatViewModel::retryPendingStateSync,
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

            AnimatedVisibility(visible = state.isStreaming) {
                GenerationStatusStrip(state.statusText)
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(
                        start = 14.dp,
                        top = 0.dp,
                        end = 14.dp,
                        bottom = 20.dp
                    )
                ) {
                    itemsIndexed(
                        items = state.messages,
                        key = { index, message -> "${message.turn}-${message.role}-$index" }
                    ) { _, message ->
                        MessageBubble(
                            message = message,
                            playerName = state.playerState?.name.orEmpty().ifBlank { "主角" },
                            regenerationEnabled = !state.isStreaming,
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

                if (showJumpToLatest) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .size(48.dp),
                        shape = CircleShape,
                        color = SurfaceLight,
                        border = BorderStroke(1.dp, OutlineSoft),
                        shadowElevation = 4.dp
                    ) {
                        IconButton(
                            onClick = {
                                autoFollowLatest = true
                                scope.launch {
                                    if (state.messages.isNotEmpty()) {
                                        listState.scrollToItem(state.messages.lastIndex)
                                        listState.scrollBy(1_000_000f)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, "回到最新消息", tint = BrightText)
                        }
                    }
                }
            }

            state.errorMessage?.let { error ->
                InlineErrorNotice(
                    message = error,
                    onRetry = if (!state.isReady && !state.isStreaming) {
                        chatViewModel::retryStateRefresh
                    } else {
                        null
                    }
                )
            }

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
                onSend = {
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
                },
                onCancel = chatViewModel::cancelStream
            )
        }
    }

    val selectedCard = selectedNpcName?.let { name ->
        state.npcCards.find { it.name == name }
    }
    selectedCard?.let { card ->
        NpcDetailDialog(
            card = card,
            playerName = state.playerState?.name.orEmpty().ifBlank { "主角" },
            characterFields = state.worldSetting?.characterFields.orEmpty(),
            onDismiss = { selectedNpcName = null }
        )
    }
    if (showPlayerDetail) {
        state.playerState?.let { player ->
            PlayerDetailDialog(
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
            title = { Text("停止生成并返回？") },
            text = { Text("本轮尚未完成，返回后未写入的剧情和行动不会保留。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLeaveConfirmation = false
                        chatViewModel.cancelStream()
                        onBack()
                    }
                ) {
                    Text("停止并返回", color = CvRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirmation = false }) {
                    Text("继续等待")
                }
            },
            shape = RoundedCornerShape(8.dp)
        )
    }
    if (showSavingNotice) {
        AlertDialog(
            onDismissRequest = { showSavingNotice = false },
            title = { Text("正在保存本轮状态") },
            text = { Text("剧情已经生成完成，角色与任务状态正在写入。完成后即可安全返回。") },
            confirmButton = {
                TextButton(onClick = { showSavingNotice = false }) {
                    Text("知道了")
                }
            },
            shape = RoundedCornerShape(8.dp)
        )
    }
    pendingRegeneration?.let { request ->
        AlertDialog(
            onDismissRequest = { pendingRegeneration = null },
            icon = { Icon(Icons.Default.Refresh, null, tint = PrimaryPink) },
            title = { Text("重新生成第${request.turn}轮？") },
            text = {
                Text(
                    "将回到第${request.turn}轮开始前，并替换这一轮及之后的旧剧情、角色和世界状态。",
                    color = DimText
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
                    Text("重新生成", color = PrimaryPink)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRegeneration = null }) {
                    Text("取消", color = DimText)
                }
            },
            shape = RoundedCornerShape(8.dp)
        )
    }
    editingAction?.let { message ->
        AlertDialog(
            onDismissRequest = { editingAction = null },
            icon = { Icon(Icons.Default.Edit, null, tint = PrimaryPink) },
            title = { Text("修改第${message.turn}轮行动") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editedActionText,
                        onValueChange = {
                            editedActionText = ChatInputPolicy.normalizeDraft(it)
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 260.dp),
                        label = { Text("行动提示词") },
                        maxLines = 10,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = BrightText,
                            unfocusedTextColor = BrightText,
                            focusedBorderColor = PrimaryPink,
                            unfocusedBorderColor = OutlineSoft
                        )
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "保存后会回到本轮开始前，并根据修改后的提示词重新生成；本轮之后的旧分支将被替换。",
                        color = DimText,
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
                    Text("保存并重新生成", color = PrimaryPink)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingAction = null }) {
                    Text("取消", color = DimText)
                }
            },
            shape = RoundedCornerShape(8.dp)
        )
    }
}

@Composable
private fun InlineErrorNotice(message: String, onRetry: (() -> Unit)? = null) {
    Surface(color = CvRed.copy(alpha = 0.12f)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
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
                color = BrightText,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.weight(1f)
            )
            if (onRetry != null) {
                TextButton(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("重试刷新")
                }
            }
        }
    }
}

@Composable
private fun StoryContextPanel(
    storyState: StoryState,
    player: PlayerState?,
    npcCards: List<CharacterCard>,
    characterFields: List<CharacterFieldDefinition>,
    isStateSyncing: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onRetryStateSync: () -> Unit,
    onPlayerClick: () -> Unit,
    onNpcClick: (CharacterCard) -> Unit
) {
    val sceneTitle = storyState.currentLocation.ifBlank { "当前场景" }
    val pendingSyncRound = StateSyncPolicy.pendingFromRound(storyState)
    val sceneMeta = listOf(storyState.timeOfDay, storyState.weather)
        .filter { it.isNotBlank() }
        .joinToString(" · ")

    Surface(color = ContextBg) {
        Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onToggleExpanded)
                    .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Place, null, tint = PrimaryPink, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        sceneTitle,
                        color = BrightText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        listOf(sceneMeta, storyState.mood)
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        color = DimText,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (npcCards.isNotEmpty()) {
                    Text("${npcCards.size} 人在场", color = NeonCyan, fontSize = 10.sp)
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    if (expanded) "收起场景状态" else "展开场景状态",
                    tint = DimText,
                    modifier = Modifier.padding(8.dp)
                )
            }

            if (pendingSyncRound != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CvYellow.copy(alpha = 0.08f))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = CvYellow,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "第${pendingSyncRound}轮起状态待补同步，角色卡可能暂时滞后",
                        color = CvYellow,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = onRetryStateSync,
                        enabled = !isStateSyncing,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        if (isStateSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                color = CvYellow,
                                strokeWidth = 1.5.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                tint = CvYellow,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(Modifier.width(3.dp))
                        Text(if (isStateSyncing) "同步中" else "补同步", color = CvYellow, fontSize = 10.sp)
                    }
                }
            }

            player?.let { currentPlayer ->
                HorizontalDivider(color = OutlineSoft)
                CompactPlayerRow(
                    player = currentPlayer,
                    characterFields = characterFields,
                    onClick = onPlayerClick
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(PrimaryPink.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Text(name.take(1), color = PrimaryPink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                color = BrightText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(role, color = DimText, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        CharacterFieldDisplayPolicy.playerPrimary(player, characterFields).forEach { field ->
            DecisionSignal(
                field.definition.label,
                compactFieldValue(field),
                characterFieldColor(field)
            )
        }
    }
}

@Composable
private fun DecisionSignal(label: String, value: String, color: Color) {
    Column(
        modifier = Modifier.width(56.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            color = DimText,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            value,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GoalRow(goal: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Flag, null, tint = PrimaryPink, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(8.dp))
        Text("当前目标", color = PrimaryPink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(goal, color = BrightText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ActiveCharacterRow(
    cards: List<CharacterCard>,
    playerName: String,
    characterFields: List<CharacterFieldDefinition>,
    onNpcClick: (CharacterCard) -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("场上角色", color = DimText, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(6.dp))
            Text("${cards.size} 人", color = DimText, fontSize = 10.sp)
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            cards.forEach { card ->
                NpcSceneCard(
                    card,
                    playerName,
                    characterFields,
                    onClick = { onNpcClick(card) }
                )
            }
        }
    }
}

@Composable
private fun NpcSceneCard(
    card: CharacterCard,
    playerName: String,
    characterFields: List<CharacterFieldDefinition>,
    onClick: () -> Unit
) {
    val relationship = NpcCardPolicy.relationshipSnapshot(card, playerName)

    Card(
        modifier = Modifier.width(174.dp).heightIn(min = 112.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = PanelBg),
        border = BorderStroke(1.dp, OutlineSoft),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(30.dp).clip(CircleShape).background(SurfaceLight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(card.name.take(1), color = PrimaryPink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(card.name, color = BrightText, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(card.occupation.ifBlank { "身份未知" }, color = DimText, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            val primaryFields = CharacterFieldDisplayPolicy.npcPrimary(
                card,
                playerName,
                characterFields
            )
            if (primaryFields.isNotEmpty()) {
                HorizontalDivider(color = OutlineSoft)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    primaryFields.forEach { field ->
                        DecisionSignal(
                            field.definition.label,
                            compactFieldValue(field),
                            characterFieldColor(field)
                        )
                    }
                }
            }
            HorizontalDivider(color = OutlineSoft)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    relationship.stage,
                    color = NeonCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "好感 ${formatFavor(relationship.favor)}",
                        color = favorColor(relationship.favor),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        "信任 ${NpcCardPolicy.trustLabel(relationship.trust)}",
                        color = trustColor(relationship.trust),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun GenerationStatusStrip(statusText: String) {
    Surface(color = PanelBg) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = PrimaryPink)
                Spacer(Modifier.width(8.dp))
                Text(statusText.ifBlank { "正在生成剧情..." }, color = DimText, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(1.dp),
                color = PrimaryPink,
                trackColor = OutlineSoft
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessageItem,
    playerName: String,
    regenerationEnabled: Boolean,
    onCopy: (String) -> Unit,
    onEdit: (ChatMessageItem) -> Unit,
    onRegenerate: (ChatMessageItem) -> Unit,
    onRetry: () -> Unit
) {
    when (message.role) {
        "user" -> UserActionBubble(
            message,
            playerName,
            regenerationEnabled,
            onCopy,
            onEdit
        )
        "error" -> ErrorMessageBubble(message, onRetry)
        else -> NarrativeMessage(message, regenerationEnabled, onRegenerate)
    }
}

@Composable
private fun UserActionBubble(
    message: ChatMessageItem,
    playerName: String,
    regenerationEnabled: Boolean,
    onCopy: (String) -> Unit,
    onEdit: (ChatMessageItem) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.End
    ) {
        Text(
            text = "$playerName · 第${message.turn}轮行动",
            color = DimText,
            fontSize = 9.sp,
            modifier = Modifier.padding(end = 2.dp, bottom = 4.dp)
        )
        Surface(
            color = UserActionBg,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Text(
                text = message.content,
                color = BrightText,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }
        Row {
            TextButton(
                onClick = { onCopy(message.content) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("复制", fontSize = 11.sp)
            }
            if (regenerationEnabled) {
                TextButton(
                    onClick = { onEdit(message) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("修改", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun NarrativeMessage(
    message: ChatMessageItem,
    regenerationEnabled: Boolean,
    onRegenerate: (ChatMessageItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("第${message.turn}轮", color = PrimaryPink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(7.dp))
            HorizontalDivider(modifier = Modifier.weight(1f), color = OutlineSoft)
            Spacer(Modifier.width(7.dp))
            Text(if (message.isStreaming) "旁白生成中" else "旁白", color = DimText, fontSize = 10.sp)
            if (regenerationEnabled && !message.isStreaming) {
                Spacer(Modifier.width(2.dp))
                TextButton(
                    onClick = { onRegenerate(message) },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("重新生成", fontSize = 10.sp)
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        Row {
            Box(
                modifier = Modifier.width(2.dp).heightIn(min = 44.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(PrimaryPink)
            )
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message.content,
                    color = BrightText,
                    fontSize = 15.sp,
                    lineHeight = 24.sp
                )
                if (message.isStreaming) {
                    Text("▊", color = PrimaryPink, fontSize = 13.sp)
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        color = CvRed.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, CvRed.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.ErrorOutline, null, tint = CvRed, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(message.content, color = BrightText, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("重试")
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
    Surface(color = PanelBg, shadowElevation = 4.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            AnimatedVisibility(visible = showSuggestions && inputText.isBlank() && suggestions.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    suggestions.forEach { suggestion ->
                        Surface(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clickable { onSuggestionSelected(suggestion) },
                            color = ContextBg,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, OutlineSoft)
                        ) {
                            Box(
                                modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    suggestion,
                                    color = BrightText,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = OutlineSoft)
            Row(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f).heightIn(min = 50.dp, max = 126.dp),
                    placeholder = {
                        Text(
                            when {
                                !isReady -> "正在加载故事..."
                                !canSubmit -> "状态待补同步"
                                else -> "描述下一步行动..."
                            },
                            color = DimText
                        )
                    },
                    enabled = isReady,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan.copy(alpha = 0.6f),
                        unfocusedBorderColor = OutlineSoft,
                        focusedContainerColor = DarkBg,
                        unfocusedContainerColor = DarkBg,
                        cursorColor = PrimaryPink,
                        focusedTextColor = BrightText,
                        unfocusedTextColor = BrightText
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = false,
                    maxLines = 4,
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (isReady && canSubmit && !isLoading && inputText.isNotBlank()) onSend()
                    })
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(
                            enabled = isReady && if (isLoading) {
                                canCancel
                            } else {
                                canSubmit && inputText.isNotBlank()
                            },
                            onClick = if (isLoading) onCancel else onSend
                        ),
                    color = when {
                        !isReady -> SurfaceLight
                        isLoading && canCancel -> CvRed.copy(alpha = 0.16f)
                        isLoading -> SurfaceLight
                        canSubmit && inputText.isNotBlank() -> PrimaryPink
                        else -> SurfaceLight
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        when {
                            isPreparing -> CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = DimText
                            )
                            isLoading && canCancel -> Icon(Icons.Default.StopCircle, "停止生成", tint = CvRed)
                            isLoading -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = DimText)
                            else -> Icon(
                                Icons.AutoMirrored.Filled.Send,
                                "发送",
                                tint = if (isReady && canSubmit && inputText.isNotBlank()) {
                                    DarkBg
                                } else {
                                    DimText
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerDetailDialog(
    player: PlayerState,
    pendingEvents: List<String>,
    characterFields: List<CharacterFieldDefinition>,
    onDismiss: () -> Unit
) {
    val goal = PlayerCardPolicy.currentGoal(player, pendingEvents)

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(8.dp), color = PanelBg) {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 720.dp).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailDialogHeader(
                    title = player.name.ifBlank { "林克" },
                    subtitle = player.class_.ifBlank { "城市居民" },
                    onDismiss = onDismiss
                )

                CharacterFieldSections(
                    CharacterFieldDisplayPolicy.playerDetails(player, characterFields)
                )
                goal.takeIf { it.isNotBlank() }?.let { DetailTextBlock("当前目标", it) }

                val activeQuests = player.questLog.filterNot { it.status.contains("完成") || it.status.contains("失败") }
                val otherPendingEvents = pendingEvents.filterNot { it.trim() == goal.trim() }
                if (activeQuests.isNotEmpty() || otherPendingEvents.isNotEmpty()) {
                    DetailSectionTitle("任务与待办")
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

                if (player.inventory.isNotEmpty()) {
                    DetailSectionTitle("随身物品")
                    player.inventory.forEach { item ->
                        DetailRow(item.name.ifBlank { "未命名物品" }, "×${item.quantity.coerceAtLeast(1)}", NeonCyan)
                    }
                }

            }
        }
    }
}

@Composable
private fun NpcDetailDialog(
    card: CharacterCard,
    playerName: String,
    characterFields: List<CharacterFieldDefinition>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(8.dp), color = PanelBg) {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 720.dp).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailDialogHeader(
                    title = card.name,
                    subtitle = card.occupation.ifBlank { "身份未知" },
                    onDismiss = onDismiss
                )

                CharacterFieldSections(
                    CharacterFieldDisplayPolicy.npcDetails(card, playerName, characterFields)
                )

                if (card.inventory.isNotEmpty()) {
                    DetailSectionTitle("持有物品")
                    card.inventory.forEach { item ->
                        DetailRow(item.name.ifBlank { "未命名物品" }, "×${item.quantity.coerceAtLeast(1)}", NeonCyan)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailDialogHeader(title: String, subtitle: String, onDismiss: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(42.dp).clip(CircleShape).background(PrimaryPink.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Text(title.take(1), color = PrimaryPink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = BrightText, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = DimText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, "关闭", tint = DimText)
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
        DetailSectionTitle(characterFieldGroupTitle(group))
        groupFields.forEach { field ->
            if (
                field.definition.valueType in setOf(
                    CharacterFieldDefinition.TYPE_TEXT,
                    CharacterFieldDefinition.TYPE_LIST
                ) && field.rawValue.length > 48
            ) {
                DetailTextBlock(field.definition.label, field.rawValue)
            } else {
                DetailRow(field.definition.label, field.value, characterFieldColor(field))
            }
        }
    }
}

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
    if (field.definition.valueType != CharacterFieldDefinition.TYPE_NUMBER) {
        return when (field.definition.group) {
            CharacterFieldDefinition.GROUP_RELATIONSHIP -> NeonCyan
            CharacterFieldDefinition.GROUP_STATUS -> PrimaryPurple
            CharacterFieldDefinition.GROUP_ABILITY -> BarPositive
            else -> BrightText
        }
    }
    val value = field.rawValue.toDoubleOrNull() ?: return BrightText
    val range = (field.definition.maxValue - field.definition.minValue).toDouble()
    if (range <= 0.0) return BrightText
    val ratio = ((value - field.definition.minValue) / range).coerceIn(0.0, 1.0)
    return when {
        ratio <= 0.2 -> CvRed
        ratio <= 0.45 -> CvYellow
        else -> NeonCyan
    }
}

@Composable
private fun DetailSectionTitle(title: String) {
    Column(modifier = Modifier.padding(top = 6.dp)) {
        HorizontalDivider(color = OutlineSoft)
        Spacer(Modifier.height(9.dp))
        Text(title, color = PrimaryPink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = DimText, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(
            text = value.ifBlank { "未知" },
            color = valueColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
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
        Text(label, color = DimText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(value, color = BrightText, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

private fun formatFavor(favor: Int): String = if (favor >= 0) "+$favor" else favor.toString()

private fun favorColor(value: Int): Color = when {
    value > 0 -> BarPositive
    value < 0 -> BarNegative
    else -> DimText
}

private fun trustColor(value: Int?): Color = when {
    value == null -> DimText
    value >= 70 -> NeonCyan
    value >= 35 -> BarPositive
    else -> CvYellow
}

private fun healthColor(value: String): Color = when {
    value.contains("濒死") || value.contains("重伤") -> CvRed
    value.contains("轻伤") || value.contains("不适") -> CvYellow
    else -> BarPositive
}

private fun staminaColor(value: Int): Color = when {
    value <= 20 -> CvRed
    value <= 45 -> CvYellow
    else -> NeonCyan
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
