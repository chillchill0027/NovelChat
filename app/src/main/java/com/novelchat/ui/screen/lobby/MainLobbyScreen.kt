package com.novelchat.ui.screen.lobby

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.novelchat.data.model.ConversationEntry
import com.novelchat.data.storage.ConversationRecoveryPolicy
import com.novelchat.ui.theme.*
import com.novelchat.ui.viewmodel.ConversationListItemPolicy
import com.novelchat.ui.viewmodel.MainLobbyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainLobbyScreen(
    viewModel: MainLobbyViewModel,
    onNewPreset: () -> Unit,
    onNewCustom: () -> Unit,
    onOpenChat: (Long) -> Unit,
    onSettings: () -> Unit
) {
    val conversations by viewModel.conversations.collectAsState()
    val operation by viewModel.operationState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDeletion by remember { mutableStateOf<ConversationEntry?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.loadConversations()
    }

    LaunchedEffect(operation.errorMessage) {
        operation.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearOperationError()
        }
    }
    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("故事库", color = PrimaryPink, fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg),
                    actions = {
                        IconButton(
                            onClick = onSettings,
                            enabled = operation.deletingId == null
                        ) {
                            Icon(Icons.Default.Settings, "API配置", tint = DimText)
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = DarkBg
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                item {
                    Text("新建故事", color = PrimaryPink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Card(modifier = Modifier.weight(1f).clickable(
                            enabled = operation.deletingId == null,
                            onClick = onNewPreset
                        ),
                            colors = CardDefaults.cardColors(containerColor = PanelBg), shape = RoundedCornerShape(8.dp)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.People, null, tint = PrimaryPink, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("导入故事", color = BrightText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("解析世界观与角色", color = DimText, fontSize = 11.sp)
                            }
                        }
                        Card(modifier = Modifier.weight(1f).clickable(
                            enabled = operation.deletingId == null,
                            onClick = onNewCustom
                        ),
                            colors = CardDefaults.cardColors(containerColor = PanelBg), shape = RoundedCornerShape(8.dp)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Edit, null, tint = PrimaryPurple, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("现代开局", color = BrightText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("城市生活模拟器", color = DimText, fontSize = 11.sp)
                            }
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("已有故事", color = PrimaryPink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                if (operation.isLoading && conversations.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = PrimaryPink)
                        }
                    }
                } else if (conversations.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("暂无故事记录\n点击上方创建新故事", color = DimText, fontSize = 13.sp, lineHeight = 20.sp)
                        }
                    }
                } else {
                    items(conversations, key = { it.id }) { conv ->
                        val hasStorageWarning =
                            conv.characterDesc == ConversationRecoveryPolicy.CORRUPT_DATA_DESCRIPTION
                        Card(Modifier.fillMaxWidth().clickable(enabled = operation.deletingId == null) { onOpenChat(conv.id) },
                            colors = CardDefaults.cardColors(containerColor = PanelBg), shape = RoundedCornerShape(8.dp)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(44.dp).background(PrimaryPink.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center) {
                                    Text(conv.characterName.take(1), color = PrimaryPink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            conv.characterName,
                                            modifier = Modifier.weight(1f),
                                            color = BrightText,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            ConversationListItemPolicy.activityLabel(conv),
                                            color = DimText,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        ConversationListItemPolicy.context(conv),
                                        color = if (hasStorageWarning) CvYellow else PrimaryPurple,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        if (hasStorageWarning) {
                                            "打开故事后将停止写入，避免覆盖原始存档"
                                        } else {
                                            ConversationListItemPolicy.preview(conv)
                                        },
                                        color = DimText,
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
                                if (operation.deletingId == conv.id) {
                                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = CvRed,
                                            strokeWidth = 2.dp
                                        )
                                    }
                                } else {
                                    IconButton(
                                        onClick = { pendingDeletion = conv },
                                        enabled = operation.deletingId == null,
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            "删除故事 ${conv.characterName}",
                                            tint = CvRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDeletion?.let { conversation ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            icon = { Icon(Icons.Default.DeleteForever, null, tint = CvRed) },
            title = { Text("删除故事？", color = BrightText) },
            text = {
                Text(
                    "“${conversation.characterName}”的剧情、角色和世界资料将被永久删除。",
                    color = DimText
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteConversation(conversation.id)
                    pendingDeletion = null
                }) {
                    Text("删除", color = CvRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletion = null }) {
                    Text("取消", color = DimText)
                }
            },
            containerColor = PanelBg,
            shape = RoundedCornerShape(8.dp)
        )
    }
}
