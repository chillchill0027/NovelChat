package com.novelchat.ui.screen.welcome

import android.content.ClipData
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelchat.data.model.CharacterFieldPolicy
import com.novelchat.ui.theme.AccentRed
import com.novelchat.ui.theme.DarkBg
import com.novelchat.ui.theme.DimText
import com.novelchat.ui.theme.GoldColor
import com.novelchat.ui.theme.PanelBg
import com.novelchat.ui.theme.SurfaceLight
import com.novelchat.ui.viewmodel.AdvancedStoryImportPolicy
import com.novelchat.ui.viewmodel.SetupViewModel
import com.novelchat.ui.viewmodel.StoryImportMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryImportScreen(
    viewModel: SetupViewModel,
    onBack: () -> Unit,
    onParsed: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    var showGuide by remember { mutableStateOf(false) }
    val handleBack = {
        viewModel.cancelStoryParsing()
        onBack()
    }
    val openDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching { readBoundedImportText(context, uri) }
                .onSuccess(viewModel::updateAdvancedStoryJson)
                .onFailure { error ->
                    viewModel.showAdvancedImportError(
                        "文件读取失败：${error.message?.take(160).orEmpty().ifBlank { "无法读取该文件" }}"
                    )
                }
        }
    }

    BackHandler(enabled = state.isParsing) { handleBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("导入故事", color = GoldColor) },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, "返回", tint = DimText)
                    }
                },
                actions = {
                    if (state.storyImportMode == StoryImportMode.ADVANCED_JSON) {
                        IconButton(onClick = { showGuide = true }) {
                            Icon(Icons.AutoMirrored.Default.HelpOutline, "高级接入指南", tint = DimText)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ImportModeTabs(
                selected = state.storyImportMode,
                enabled = !state.isParsing,
                onSelected = viewModel::selectStoryImportMode
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    when (state.storyImportMode) {
                        StoryImportMode.AI_RECOGNITION -> AiRecognitionEditor(
                            value = state.storyText,
                            enabled = !state.isParsing,
                            onValueChange = viewModel::updateStoryText
                        )

                        StoryImportMode.ADVANCED_JSON -> AdvancedJsonEditor(
                            value = state.advancedStoryJson,
                            enabled = !state.isParsing,
                            onValueChange = viewModel::updateAdvancedStoryJson,
                            onOpenFile = {
                                openDocument.launch(
                                    arrayOf("application/json", "text/plain", "application/octet-stream")
                                )
                            },
                            onPaste = {
                                coroutineScope.launch {
                                    val text = clipboard.getClipEntry()
                                        ?.clipData
                                        ?.takeIf { it.itemCount > 0 }
                                        ?.getItemAt(0)
                                        ?.coerceToText(context)
                                        ?.toString()
                                    text?.takeIf(String::isNotBlank)
                                        ?.let(viewModel::updateAdvancedStoryJson)
                                        ?: viewModel.showAdvancedImportError("剪贴板中没有可用文本")
                                }
                            },
                            onLoadTemplate = viewModel::loadAdvancedStoryTemplate
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                state.parseError?.let { error ->
                    Text(
                        error,
                        color = AccentRed,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                state.advancedImportSummary?.let { summary ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = GoldColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(summary, color = GoldColor, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }

                Button(
                    onClick = {
                        when (state.storyImportMode) {
                            StoryImportMode.AI_RECOGNITION -> viewModel.parseStory()
                            StoryImportMode.ADVANCED_JSON -> viewModel.parseAdvancedStory()
                        }
                    },
                    enabled = !state.isParsing && when (state.storyImportMode) {
                        StoryImportMode.AI_RECOGNITION -> state.storyText.isNotBlank()
                        StoryImportMode.ADVANCED_JSON -> state.advancedStoryJson.isNotBlank()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldColor)
                ) {
                    if (state.isParsing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = LocalContentColor.current,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(
                        when {
                            state.isParsing -> "解析中..."
                            state.storyImportMode == StoryImportMode.ADVANCED_JSON -> "校验并生成预览"
                            else -> "解析故事设定"
                        }
                    )
                }

                if (state.parseResult != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onParsed,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("查看并确认全部设定")
                    }
                }
            }
        }
    }

    if (showGuide) {
        AdvancedImportGuideSheet(
            onDismiss = { showGuide = false },
            onLoadTemplate = {
                viewModel.loadAdvancedStoryTemplate()
                showGuide = false
            },
            onCopyTemplate = {
                coroutineScope.launch {
                    clipboard.setClipEntry(
                        ClipEntry(
                            ClipData.newPlainText(
                                "NovelChat Advanced Story Import v1",
                                AdvancedStoryImportPolicy.template
                            )
                        )
                    )
                }
            }
        )
    }
}

@Composable
private fun ImportModeTabs(
    selected: StoryImportMode,
    enabled: Boolean,
    onSelected: (StoryImportMode) -> Unit
) {
    val modes = listOf(
        StoryImportMode.AI_RECOGNITION to "AI 识别",
        StoryImportMode.ADVANCED_JSON to "高级接入"
    )
    TabRow(
        selectedTabIndex = modes.indexOfFirst { it.first == selected },
        containerColor = DarkBg,
        contentColor = GoldColor,
        indicator = { positions ->
            TabRowDefaults.SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(
                    positions[modes.indexOfFirst { it.first == selected }]
                ),
                color = GoldColor
            )
        },
        divider = { HorizontalDivider(color = SurfaceLight) }
    ) {
        modes.forEach { (mode, label) ->
            Tab(
                selected = selected == mode,
                onClick = { onSelected(mode) },
                enabled = enabled,
                text = { Text(label) },
                icon = {
                    Icon(
                        if (mode == StoryImportMode.AI_RECOGNITION) {
                            Icons.Default.AutoAwesome
                        } else {
                            Icons.Default.DataObject
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                selectedContentColor = GoldColor,
                unselectedContentColor = DimText
            )
        }
    }
}

@Composable
private fun AiRecognitionEditor(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "输入自然语言设定，AI 将识别世界、具名角色、开局和可检索世界书。",
            color = DimText,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        ImportTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            placeholder = "例如：当代城市生活，主角林克刚搬进云桥里……",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AdvancedJsonEditor(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onOpenFile: () -> Unit,
    onPaste: () -> Unit,
    onLoadTemplate: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "JSON v${AdvancedStoryImportPolicy.SCHEMA_VERSION}",
                color = GoldColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onOpenFile, enabled = enabled) {
                Icon(Icons.Default.FolderOpen, "打开 JSON 文件", tint = DimText)
            }
            IconButton(onClick = onPaste, enabled = enabled) {
                Icon(Icons.Default.ContentPaste, "从剪贴板粘贴", tint = DimText)
            }
            IconButton(onClick = onLoadTemplate, enabled = enabled) {
                Icon(Icons.AutoMirrored.Default.NoteAdd, "载入完整模板", tint = DimText)
            }
        }
        ImportTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            placeholder = "粘贴 JSON、打开文件，或载入模板",
            modifier = Modifier.weight(1f),
            monospace = true
        )
        Text(
            "${value.length} / ${AdvancedStoryImportPolicy.MAX_JSON_LENGTH}",
            color = DimText,
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = 4.dp)
        )
    }
}

@Composable
private fun ImportTextField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    placeholder: String,
    modifier: Modifier = Modifier,
    monospace: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = DimText) },
        textStyle = androidx.compose.ui.text.TextStyle(
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            fontSize = if (monospace) 12.sp else 14.sp,
            lineHeight = if (monospace) 18.sp else 21.sp
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = GoldColor.copy(alpha = 0.6f),
            unfocusedBorderColor = SurfaceLight,
            focusedContainerColor = PanelBg,
            unfocusedContainerColor = PanelBg,
            cursorColor = GoldColor,
            focusedTextColor = GoldColor,
            unfocusedTextColor = GoldColor
        ),
        shape = RoundedCornerShape(8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedImportGuideSheet(
    onDismiss: () -> Unit,
    onLoadTemplate: () -> Unit,
    onCopyTemplate: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = PanelBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("高级接入指南", color = GoldColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            GuideSection("接入流程") {
                GuideText("准备 UTF-8 JSON → 打开文件或粘贴 → 校验 → 查看完整预览 → 确认创建。校验不会调用 AI，也不会消耗模型额度。")
            }
            GuideSection("必填结构") {
                GuideCode("schema_version: 1")
                GuideCode("world_setting: 世界名称、摘要与 lore")
                GuideCode("player: 必须使用主角真实姓名")
                GuideCode("npcs / worldbook / opening: 可扩展数组与对象")
            }
            GuideSection("角色字段完全自定义") {
                GuideText("在 world_setting.character_fields 中可重命名体力、魅力、性格、经历等内置字段，也可新增任意字段。每项可定义类型、范围、单位、适用角色、首页或详情显示，以及 dynamic、event、manual 刷新模式。")
                GuideCode("character_fields: [{ key, label, value_type, applies_to, display, update_mode }]")
                GuideCode("player / npcs[].custom_attributes: 自定义字段初始值")
            }
            GuideSection("长期设定与即时状态") {
                GuideText("dynamic 字段随本轮明确变化刷新；event 字段只在重大事件或稳定成长成立时刷新；manual 字段仅使用导入值。性格、经历、技能和能力可随剧情证据更新，世界规则与人物 lore 仍作为稳定设定保存。")
            }
            GuideSection("世界书") {
                GuideText("具体地点、组织、物品和事件应拆成 worldbook 条目，并填写名称、别名和触发词。应用每轮只加载相关条目，详细设定不会挤占全部上下文。")
            }
            GuideSection("角色姓名") {
                GuideText("主角与所有会互动的 NPC 都必须有唯一真实姓名。身份写 occupation 或 role，例如姓名“王常”、职业“邻居”，不要把“邻居A”当姓名。")
            }
            GuideSection("秘密与可见性") {
                GuideText("人物幕后信息放 lore.secrets；世界资料使用 is_secret 与 known_by_player。秘密会提供给叙事模型保持一致，但正文仍应遵守角色知识边界。")
            }
            GuideSection("容量") {
                GuideText("最多 ${AdvancedStoryImportPolicy.MAX_NPCS} 位 NPC、${AdvancedStoryImportPolicy.MAX_WORLDBOOK_ENTRIES} 条世界书、${CharacterFieldPolicy.MAX_FIELDS} 个自定义角色字段、${AdvancedStoryImportPolicy.MAX_JSON_LENGTH} 个字符。custom_attributes 必须先在 character_fields 中定义。")
            }
            Button(
                onClick = onLoadTemplate,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = GoldColor),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.AutoMirrored.Default.NoteAdd, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("载入完整模板")
            }
            OutlinedButton(
                onClick = onCopyTemplate,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("复制模板")
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("关闭")
            }
        }
    }
}

@Composable
private fun GuideSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = GoldColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun GuideText(text: String) {
    Text(text, color = DimText, fontSize = 13.sp, lineHeight = 19.sp)
}

@Composable
private fun GuideCode(text: String) {
    Text(
        text,
        color = GoldColor,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = 18.sp
    )
}

private fun readBoundedImportText(context: Context, uri: Uri): String {
    val input = context.contentResolver.openInputStream(uri)
        ?: throw IllegalArgumentException("无法打开文件")
    return input.bufferedReader(Charsets.UTF_8).use { reader ->
        val result = StringBuilder()
        val buffer = CharArray(4_096)
        while (true) {
            val count = reader.read(buffer)
            if (count < 0) break
            if (result.length + count > AdvancedStoryImportPolicy.MAX_JSON_LENGTH) {
                throw IllegalArgumentException(
                    "文件超过 ${AdvancedStoryImportPolicy.MAX_JSON_LENGTH} 个字符"
                )
            }
            result.append(buffer, 0, count)
        }
        result.toString()
    }
}
