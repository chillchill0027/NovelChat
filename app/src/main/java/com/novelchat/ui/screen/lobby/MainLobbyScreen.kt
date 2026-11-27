package com.novelchat.ui.screen.lobby

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.novelchat.data.model.ConversationEntry
import com.novelchat.data.storage.ConversationRecoveryPolicy
import com.novelchat.ui.theme.AmberContainer
import com.novelchat.ui.theme.AmberPrimary
import com.novelchat.ui.theme.CenteredAdaptiveBox
import com.novelchat.ui.theme.CvRed
import com.novelchat.ui.theme.CvYellow
import com.novelchat.ui.theme.EditorialAvatar
import com.novelchat.ui.theme.Hairline
import com.novelchat.ui.theme.InkMuted
import com.novelchat.ui.theme.InkPrimary
import com.novelchat.ui.theme.InkSecondary
import com.novelchat.ui.theme.MistContainer
import com.novelchat.ui.theme.MistSecondary
import com.novelchat.ui.theme.ObsidianCanvas
import com.novelchat.ui.theme.SectionOverline
import com.novelchat.ui.theme.SurfaceBase
import com.novelchat.ui.theme.SurfaceHigh
import com.novelchat.ui.theme.SurfaceLow
import com.novelchat.ui.theme.TonalBadge
import com.novelchat.ui.theme.rememberAdaptiveSpec
import com.novelchat.ui.viewmodel.ConversationListItemPolicy
import com.novelchat.ui.viewmodel.MainLobbyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainLobbyScreen(
    viewModel: MainLobbyViewModel,
    onStartDefaultStory: () -> Unit,
    onImportStory: () -> Unit,
    onOpenChat: (Long) -> Unit,
    onSettings: () -> Unit
) {
    val conversations by viewModel.conversations.collectAsState()
    val operation by viewModel.operationState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDeletion by remember { mutableStateOf<ConversationEntry?>(null) }
    val adaptive = rememberAdaptiveSpec()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.loadConversations()
    }

    LaunchedEffect(operation.errorMessage) {
        operation.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearOperationError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "故事库",
                            color = InkPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 24.sp,
                            letterSpacing = (-0.2).sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianCanvas),
                actions = {
                    Surface(
                        shape = CircleShape,
                        color = SurfaceBase,
                        border = BorderStroke(1.dp, Hairline),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        IconButton(
                            onClick = onSettings,
                            enabled = operation.deletingId == null,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "API配置",
                                tint = InkSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = ObsidianCanvas
    ) { padding ->
        CenteredAdaptiveBox(
            maxWidth = adaptive.contentMaxWidth,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = adaptive.horizontalPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 36.dp)
            ) {
            item {
                SectionOverline(
                    title = "开启新篇章",
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickStartCard(
                        title = if (operation.isCreating) "正在开局..." else "神雕侠侣",
                        subtitle = "扮演杨过 · 终南烟雨",
                        icon = Icons.Default.Explore,
                        iconBg = AmberContainer,
                        iconTint = AmberPrimary,
                        enabled = !operation.isCreating && operation.deletingId == null,
                        onClick = onStartDefaultStory,
                        modifier = Modifier.weight(1f)
                    )
                    QuickStartCard(
                        title = "导入故事",
                        subtitle = "AI 解析或高级 JSON",
                        icon = Icons.Default.AutoAwesome,
                        iconBg = MistContainer,
                        iconTint = MistSecondary,
                        enabled = !operation.isCreating && operation.deletingId == null,
                        onClick = onImportStory,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                SectionOverline(
                    title = "继续阅读",
                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                    trailing = {
                        if (conversations.isNotEmpty()) {
                            TonalBadge(
                                text = "${conversations.size} 部作品",
                                containerColor = SurfaceLow,
                                contentColor = InkMuted
                            )
                        }
                    }
                )
            }

            if (operation.isLoading && conversations.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AmberPrimary, strokeWidth = 2.5.dp)
                    }
                }
            } else if (conversations.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = SurfaceLow,
                        border = BorderStroke(1.dp, Hairline)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceBase),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = null,
                                    tint = InkMuted,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = "书架还是空的",
                                color = InkPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "点击上方立即开启《神雕侠侣》，或导入自定义故事设定",
                                color = InkSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else if (adaptive.cardGridColumns > 1) {
                val rows = conversations.chunked(adaptive.cardGridColumns)
                items(rows, key = { row -> row.joinToString("-") { it.id.toString() } }) { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowItems.forEach { conv ->
                            Box(modifier = Modifier.weight(1f)) {
                                StoryLibraryCard(
                                    conversation = conv,
                                    isDeleting = operation.deletingId == conv.id,
                                    interactionsEnabled = operation.deletingId == null,
                                    onOpen = { onOpenChat(conv.id) },
                                    onRequestDelete = { pendingDeletion = conv }
                                )
                            }
                        }
                        repeat(adaptive.cardGridColumns - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                items(conversations, key = { it.id }) { conv ->
                    StoryLibraryCard(
                        conversation = conv,
                        isDeleting = operation.deletingId == conv.id,
                        interactionsEnabled = operation.deletingId == null,
                        onOpen = { onOpenChat(conv.id) },
                        onRequestDelete = { pendingDeletion = conv }
                    )
                }
            }
        }
        }
    }

    pendingDeletion?.let { conversation ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = CvRed
                )
            },
            title = {
                Text(
                    text = "删除故事？",
                    color = InkPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = "“${conversation.characterName}”的剧情、角色和世界资料将被永久删除，且无法恢复。",
                    color = InkSecondary,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteConversation(conversation.id)
                        pendingDeletion = null
                    }
                ) {
                    Text("删除", color = CvRed, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletion = null }) {
                    Text("取消", color = InkSecondary)
                }
            },
            containerColor = SurfaceHigh,
            shape = RoundedCornerShape(28.dp)
        )
    }
}

@Composable
private fun QuickStartCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = SurfaceBase,
        border = BorderStroke(1.dp, Hairline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = title,
                color = InkPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = subtitle,
                color = InkSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StoryLibraryCard(
    conversation: ConversationEntry,
    isDeleting: Boolean,
    interactionsEnabled: Boolean,
    onOpen: () -> Unit,
    onRequestDelete: () -> Unit
) {
    val hasStorageWarning =
        conversation.characterDesc == ConversationRecoveryPolicy.CORRUPT_DATA_DESCRIPTION

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = interactionsEnabled, onClick = onOpen),
        shape = RoundedCornerShape(20.dp),
        color = SurfaceBase,
        border = BorderStroke(
            width = 1.dp,
            color = if (hasStorageWarning) CvYellow.copy(alpha = 0.45f) else Hairline
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EditorialAvatar(
                    name = conversation.characterName,
                    size = 46.dp,
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = conversation.characterName,
                            color = InkPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(8.dp))
                        TonalBadge(
                            text = ConversationListItemPolicy.activityLabel(conversation),
                            containerColor = SurfaceHigh,
                            contentColor = InkSecondary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = ConversationListItemPolicy.context(conversation),
                        color = if (hasStorageWarning) CvYellow else AmberPrimary.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (isDeleting) {
                    Box(
                        modifier = Modifier.size(44.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = CvRed,
                            strokeWidth = 2.dp
                        )
                    }
                } else {
                    IconButton(
                        onClick = onRequestDelete,
                        enabled = interactionsEnabled,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "删除故事 ${conversation.characterName}",
                            tint = InkMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = if (hasStorageWarning) {
                    "打开故事后将停止写入，避免覆盖原始存档"
                } else {
                    ConversationListItemPolicy.preview(conversation)
                },
                color = InkSecondary,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}
