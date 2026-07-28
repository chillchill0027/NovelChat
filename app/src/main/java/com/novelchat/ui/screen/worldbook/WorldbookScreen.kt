package com.novelchat.ui.screen.worldbook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelchat.data.model.WorldbookEntry
import com.novelchat.ui.theme.*
import com.novelchat.ui.viewmodel.WorldbookViewModel
import com.novelchat.ui.viewmodel.WorldbookEntryInputPolicy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldbookScreen(
    characterId: Long,
    onBack: () -> Unit,
    viewModel: WorldbookViewModel
) {
    var editingEntry by remember { mutableStateOf<WorldbookEntry?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<WorldbookEntry?>(null) }
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isMutating by viewModel.isMutating.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(characterId) {
        viewModel.loadEntries(characterId)
    }
    LaunchedEffect(errorMessage) {
        errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("世界书", color = GoldColor) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isMutating) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, "返回", tint = DimText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        floatingActionButton = {
            if (!isLoading && !isMutating) {
                FloatingActionButton(
                    onClick = {
                        editingEntry = null
                        showEditor = true
                    },
                    containerColor = GoldColor,
                    contentColor = DarkBg
                ) {
                    Icon(Icons.Default.Add, contentDescription = "新增世界书")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = DarkBg
    ) { padding ->
        val entries by viewModel.entries.collectAsState()

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            worldbookTypes.forEach { (type, label) ->
                val filtered = entries.filter { it.type == type }
                if (filtered.isNotEmpty()) {
                    item {
                        Text(label, color = GoldColor, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    }
                    itemsIndexed(
                        items = filtered,
                        key = { index, entry -> entry.listKey(type, index) }
                    ) { _, entry ->
                        WorldbookCard(
                            entry = entry,
                            onClick = {
                                editingEntry = entry
                                showEditor = true
                            }
                        )
                    }
                }
            }

            val otherEntries = entries.filter { entry ->
                worldbookTypes.none { typeEntry -> typeEntry.first == entry.type }
            }
            if (otherEntries.isNotEmpty()) {
                item {
                    Text(
                        "其他",
                        color = GoldColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                itemsIndexed(
                    items = otherEntries,
                    key = { index, entry -> entry.listKey("other", index) }
                ) { _, entry ->
                    WorldbookCard(
                        entry = entry,
                        onClick = {
                            editingEntry = entry
                            showEditor = true
                        }
                    )
                }
            }

            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = GoldColor)
                    }
                }
            } else if (entries.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("暂无世界书条目", color = DimText)
                    }
                }
            }
        }
    }

    if (showEditor) {
        WorldbookEditorDialog(
            entry = editingEntry,
            isBusy = isMutating,
            onDismiss = { if (!isMutating) showEditor = false },
            onSave = { entry, type, name, description, keywords, status, isActive ->
                viewModel.saveEntry(entry, type, name, description, keywords, status, isActive) {
                    showEditor = false
                }
            },
            onDelete = { entry ->
                pendingDelete = entry
                showEditor = false
            }
        )
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = {
                if (!isMutating) {
                    pendingDelete = null
                    showEditor = true
                }
            },
            title = { Text("删除世界书条目", color = GoldColor) },
            text = { Text("确定删除“${entry.name}”吗？此操作无法撤销。", color = BrightText) },
            confirmButton = {
                TextButton(
                    enabled = !isMutating,
                    onClick = {
                        pendingDelete = null
                        showEditor = true
                        viewModel.deleteEntry(entry) { showEditor = false }
                    }
                ) {
                    Text("删除", color = AccentRed)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isMutating,
                    onClick = {
                        pendingDelete = null
                        showEditor = true
                    }
                ) {
                    Text("取消", color = DimText)
                }
            },
            containerColor = PanelBg
        )
    }
}

private fun WorldbookEntry.listKey(section: String, index: Int): String =
    "$section:${id.trim()}:${name.trim()}:$index"

@Composable
private fun WorldbookCard(entry: WorldbookEntry, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = PanelBg),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when (entry.type) {
                        "location" -> Icons.Default.Place
                        "item" -> Icons.Default.ShoppingCart
                        "organization" -> Icons.Default.Group
                        "event" -> Icons.Default.Event
                        else -> Icons.Default.Description
                    },
                    contentDescription = null,
                    tint = GoldColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    entry.name,
                    color = GoldColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    entry.status,
                    color = DimText,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 96.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                entry.description,
                color = DimText,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.keywords.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    entry.keywords.take(6).joinToString(" / "),
                    color = GoldColor.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun WorldbookEditorDialog(
    entry: WorldbookEntry?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (WorldbookEntry?, String, String, String, List<String>, String, Boolean) -> Unit,
    onDelete: (WorldbookEntry) -> Unit
) {
    var type by remember(entry) { mutableStateOf(entry?.type ?: "location") }
    var name by remember(entry) { mutableStateOf(entry?.name.orEmpty()) }
    var description by remember(entry) { mutableStateOf(entry?.description.orEmpty()) }
    var keywordsText by remember(entry) { mutableStateOf(entry?.keywords?.joinToString(", ").orEmpty()) }
    var status by remember(entry) { mutableStateOf(entry?.status ?: "正常") }
    var isActive by remember(entry) { mutableStateOf(entry?.isActive ?: true) }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text(if (entry == null) "新增世界书" else "编辑世界书", color = GoldColor) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("类型", color = DimText, fontSize = 12.sp)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    worldbookTypes.chunked(3).forEach { rowTypes ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            rowTypes.forEach { (value, label) ->
                                FilterChip(
                                    selected = type == value,
                                    enabled = !isBusy,
                                    onClick = { type = value },
                                    label = { Text(label, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = GoldColor.copy(alpha = 0.22f),
                                        selectedLabelColor = GoldColor,
                                        labelColor = DimText
                                    )
                                )
                            }
                        }
                    }
                }

                EditorField(
                    "名称",
                    name,
                    { name = WorldbookEntryInputPolicy.normalizeNameDraft(it) },
                    enabled = !isBusy,
                    singleLine = true
                )
                EditorField(
                    "描述",
                    description,
                    { description = WorldbookEntryInputPolicy.normalizeDescriptionDraft(it) },
                    enabled = !isBusy,
                    minLines = 3
                )
                EditorField(
                    "关键词",
                    keywordsText,
                    { keywordsText = WorldbookEntryInputPolicy.normalizeKeywordsDraft(it) },
                    enabled = !isBusy,
                    placeholder = "用逗号分隔，例如：王城, 王宫"
                )
                EditorField(
                    "状态",
                    status,
                    { status = WorldbookEntryInputPolicy.normalizeStatusDraft(it) },
                    enabled = !isBusy,
                    singleLine = true
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("参与提示词注入", color = DimText, modifier = Modifier.weight(1f), fontSize = 13.sp)
                    Switch(
                        checked = isActive,
                        onCheckedChange = { isActive = it },
                        enabled = !isBusy,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = GoldColor,
                            checkedTrackColor = GoldColor.copy(alpha = 0.35f)
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        entry,
                        type,
                        name,
                        description,
                        keywordsText.split(',', '，', '、', '/', '\n'),
                        status,
                        isActive
                    )
                },
                enabled = name.isNotBlank() && !isBusy
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = GoldColor,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("保存", color = GoldColor)
                }
            }
        },
        dismissButton = {
            Row {
                if (entry != null) {
                    TextButton(onClick = { onDelete(entry) }, enabled = !isBusy) {
                        Text("删除", color = AccentRed)
                    }
                }
                TextButton(onClick = onDismiss, enabled = !isBusy) {
                    Text("取消", color = DimText)
                }
            }
        },
        containerColor = PanelBg
    )
}

@Composable
private fun EditorField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
    placeholder: String = "",
    singleLine: Boolean = false,
    minLines: Int = 1
) {
    Text(label, color = DimText, fontSize = 12.sp)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        placeholder = if (placeholder.isBlank()) null else ({ Text(placeholder, color = DimText) }),
        singleLine = singleLine,
        minLines = minLines,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = GoldColor.copy(alpha = 0.5f),
            unfocusedBorderColor = SurfaceLight,
            focusedContainerColor = DarkBg,
            unfocusedContainerColor = DarkBg,
            cursorColor = GoldColor,
            focusedTextColor = BrightText,
            unfocusedTextColor = BrightText
        ),
        shape = RoundedCornerShape(8.dp)
    )
}

private val worldbookTypes = listOf(
    "location" to "地点",
    "item" to "物品",
    "organization" to "组织",
    "event" to "事件",
    "character" to "角色"
)
