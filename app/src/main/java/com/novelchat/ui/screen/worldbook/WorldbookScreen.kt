package com.novelchat.ui.screen.worldbook

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.novelchat.data.model.WorldbookEntry
import com.novelchat.ui.theme.AmberContainer
import com.novelchat.ui.theme.AmberPrimary
import com.novelchat.ui.theme.CenteredAdaptiveBox
import com.novelchat.ui.theme.CvRed
import com.novelchat.ui.theme.Hairline
import com.novelchat.ui.theme.InkMuted
import com.novelchat.ui.theme.InkPrimary
import com.novelchat.ui.theme.InkSecondary
import com.novelchat.ui.theme.MistContainer
import com.novelchat.ui.theme.MistSecondary
import com.novelchat.ui.theme.NovelFieldShape
import com.novelchat.ui.theme.ObsidianCanvas
import com.novelchat.ui.theme.OnAmberContainer
import com.novelchat.ui.theme.OnAmberPrimary
import com.novelchat.ui.theme.PrimaryPillButton
import com.novelchat.ui.theme.SageContainer
import com.novelchat.ui.theme.SageTertiary
import com.novelchat.ui.theme.SectionOverline
import com.novelchat.ui.theme.SurfaceBase
import com.novelchat.ui.theme.SurfaceHigh
import com.novelchat.ui.theme.SurfaceLow
import com.novelchat.ui.theme.TonalBadge
import com.novelchat.ui.theme.novelTextFieldColors
import com.novelchat.ui.theme.rememberAdaptiveSpec
import com.novelchat.ui.viewmodel.WorldbookEntryInputPolicy
import com.novelchat.ui.viewmodel.WorldbookViewModel

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
    var selectedTypeFilter by remember { mutableStateOf<String?>(null) }
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isMutating by viewModel.isMutating.collectAsState()
    val entries by viewModel.entries.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val adaptive = rememberAdaptiveSpec()

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
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "世界书",
                            color = InkPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (entries.isNotEmpty()) {
                            TonalBadge(
                                text = "${entries.size} 条",
                                containerColor = SurfaceBase,
                                contentColor = InkSecondary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isMutating) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, "返回", tint = InkSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianCanvas)
            )
        },
        floatingActionButton = {
            if (!isLoading && !isMutating) {
                ExtendedFloatingActionButton(
                    onClick = {
                        editingEntry = null
                        showEditor = true
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = "新增世界书") },
                    text = { Text("新增条目", fontWeight = FontWeight.SemiBold) },
                    containerColor = AmberPrimary,
                    contentColor = OnAmberPrimary,
                    shape = RoundedCornerShape(18.dp)
                )
            }
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
        Column(modifier = Modifier.fillMaxSize()) {
            // Horizontal category filter pills
            if (entries.isNotEmpty()) {
                val presentTypes = worldbookTypes.filter { (type, _) ->
                    entries.any { it.type == type }
                }
                if (presentTypes.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = adaptive.horizontalPadding, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryFilterPill(
                            label = "全部 (${entries.size})",
                            selected = selectedTypeFilter == null,
                            onClick = { selectedTypeFilter = null }
                        )
                        presentTypes.forEach { (type, label) ->
                            val count = entries.count { it.type == type }
                            CategoryFilterPill(
                                label = "$label ($count)",
                                selected = selectedTypeFilter == type,
                                onClick = {
                                    selectedTypeFilter = if (selectedTypeFilter == type) null else type
                                }
                            )
                        }
                    }
                }
            }

            val visibleEntries = if (selectedTypeFilter == null) {
                entries
            } else {
                entries.filter { it.type == selectedTypeFilter }
            }
            val columns = adaptive.denseGridColumns

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = adaptive.horizontalPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
            ) {
                worldbookTypes.forEach { (type, label) ->
                    val filtered = visibleEntries.filter { it.type == type }
                    if (filtered.isNotEmpty()) {
                        item {
                            SectionOverline(
                                title = label,
                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                            )
                        }
                        if (columns > 1) {
                            val rows = filtered.chunked(columns)
                            itemsIndexed(
                                items = rows,
                                key = { rowIdx, row -> "$type-row-$rowIdx-${row.firstOrNull()?.id.orEmpty()}" }
                            ) { _, rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    rowItems.forEach { entry ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            WorldbookCard(
                                                entry = entry,
                                                onClick = {
                                                    editingEntry = entry
                                                    showEditor = true
                                                }
                                            )
                                        }
                                    }
                                    repeat(columns - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        } else {
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
                }

                val otherEntries = visibleEntries.filter { entry ->
                    worldbookTypes.none { typeEntry -> typeEntry.first == entry.type }
                }
                if (otherEntries.isNotEmpty()) {
                    item {
                        SectionOverline(
                            title = "其他",
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                        )
                    }
                    if (columns > 1) {
                        val rows = otherEntries.chunked(columns)
                        itemsIndexed(
                            items = rows,
                            key = { rowIdx, row -> "other-row-$rowIdx-${row.firstOrNull()?.id.orEmpty()}" }
                        ) { _, rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowItems.forEach { entry ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        WorldbookCard(
                                            entry = entry,
                                            onClick = {
                                                editingEntry = entry
                                                showEditor = true
                                            }
                                        )
                                    }
                                }
                                repeat(columns - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
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
                }

                if (isLoading) {
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
                } else if (visibleEntries.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("暂无世界书条目", color = InkSecondary, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
        }
    }

    if (showEditor) {
        WorldbookEditorSheet(
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
            title = { Text("删除世界书条目？", color = InkPrimary, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    text = "确定删除“${entry.name}”吗？此操作无法撤销。",
                    color = InkSecondary,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isMutating,
                    onClick = {
                        pendingDelete = null
                        showEditor = true
                        viewModel.deleteEntry(entry) { showEditor = false }
                    }
                ) {
                    Text("删除", color = CvRed, fontWeight = FontWeight.SemiBold)
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
                    Text("取消", color = InkSecondary)
                }
            },
            containerColor = SurfaceHigh,
            shape = RoundedCornerShape(28.dp)
        )
    }
}

private fun WorldbookEntry.listKey(section: String, index: Int): String =
    "$section:${id.trim()}:${name.trim()}:$index"

@Composable
private fun CategoryFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = if (selected) AmberContainer else SurfaceBase,
        border = BorderStroke(
            1.dp,
            if (selected) AmberPrimary.copy(alpha = 0.55f) else Hairline
        )
    ) {
        Text(
            text = label,
            color = if (selected) OnAmberContainer else InkSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp)
        )
    }
}

private fun worldbookCategoryStyle(type: String): Triple<ImageVector, Color, Color> = when (type) {
    "location" -> Triple(Icons.Default.Place, AmberContainer, AmberPrimary)
    "organization" -> Triple(Icons.Default.Group, MistContainer, MistSecondary)
    "event" -> Triple(Icons.Default.Event, SageContainer, SageTertiary)
    "item" -> Triple(Icons.Default.ShoppingCart, SurfaceHigh, InkPrimary)
    else -> Triple(Icons.Default.Description, SurfaceHigh, InkSecondary)
}

@Composable
private fun WorldbookCard(entry: WorldbookEntry, onClick: () -> Unit) {
    val (icon, iconBg, iconTint) = worldbookCategoryStyle(entry.type)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = SurfaceBase,
        border = BorderStroke(1.dp, Hairline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = entry.name,
                    color = InkPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (entry.status.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    TonalBadge(
                        text = entry.status,
                        containerColor = SurfaceHigh,
                        contentColor = InkSecondary,
                        modifier = Modifier.widthIn(max = 136.dp)
                    )
                }
            }
            if (entry.description.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = entry.description,
                    color = InkSecondary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (entry.keywords.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    entry.keywords.take(5).forEach { keyword ->
                        Surface(
                            shape = CircleShape,
                            color = SurfaceLow
                        ) {
                            Text(
                                text = keyword,
                                color = InkMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
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
private fun WorldbookEditorSheet(
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { if (!isBusy) onDismiss() },
        sheetState = sheetState,
        containerColor = SurfaceBase,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .heightIn(max = 680.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (entry == null) "新增世界书条目" else "编辑世界书条目",
                    color = InkPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (entry != null) {
                    IconButton(onClick = { onDelete(entry) }, enabled = !isBusy) {
                        Icon(Icons.Default.DeleteOutline, "删除", tint = CvRed)
                    }
                }
                IconButton(onClick = { if (!isBusy) onDismiss() }) {
                    Icon(Icons.Default.Close, "关闭", tint = InkSecondary)
                }
            }

            Text("条目类型", color = InkSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                worldbookTypes.forEach { (value, label) ->
                    CategoryFilterPill(
                        label = label,
                        selected = type == value,
                        onClick = { if (!isBusy) type = value }
                    )
                }
            }

            EditorField(
                label = "名称",
                value = name,
                onValueChange = { name = WorldbookEntryInputPolicy.normalizeNameDraft(it) },
                enabled = !isBusy,
                singleLine = true
            )
            EditorField(
                label = "详细描述",
                value = description,
                onValueChange = { description = WorldbookEntryInputPolicy.normalizeDescriptionDraft(it) },
                enabled = !isBusy,
                minLines = 3
            )
            EditorField(
                label = "触发关键词",
                value = keywordsText,
                onValueChange = { keywordsText = WorldbookEntryInputPolicy.normalizeKeywordsDraft(it) },
                enabled = !isBusy,
                placeholder = "用逗号分隔，例如：茶馆, 七号码头"
            )
            EditorField(
                label = "当前状态",
                value = status,
                onValueChange = { status = WorldbookEntryInputPolicy.normalizeStatusDraft(it) },
                enabled = !isBusy,
                singleLine = true
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = SurfaceLow,
                border = BorderStroke(1.dp, Hairline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("参与提示词注入", color = InkPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("关闭后仍保留记录，但不会自动注入后续剧情", color = InkMuted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = isActive,
                        onCheckedChange = { isActive = it },
                        enabled = !isBusy,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = OnAmberPrimary,
                            checkedTrackColor = AmberPrimary
                        )
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            PrimaryPillButton(
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
                        modifier = Modifier.size(18.dp),
                        color = LocalContentColor.current,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = if (isBusy) "保存中..." else "保存条目",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
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
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = InkSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            placeholder = if (placeholder.isBlank()) null else ({ Text(placeholder) }),
            singleLine = singleLine,
            minLines = minLines,
            colors = novelTextFieldColors(SurfaceLow),
            shape = NovelFieldShape
        )
    }
}

private val worldbookTypes = listOf(
    "location" to "地点",
    "item" to "物品",
    "organization" to "组织",
    "event" to "事件",
    "character" to "角色"
)
