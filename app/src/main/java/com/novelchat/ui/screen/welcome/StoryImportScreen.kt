package com.novelchat.ui.screen.welcome

import android.content.ClipData
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelchat.data.model.CharacterFieldPolicy
import com.novelchat.ui.theme.AmberPrimary
import com.novelchat.ui.theme.CenteredAdaptiveBox
import com.novelchat.ui.theme.ErrorContainer
import com.novelchat.ui.theme.Hairline
import com.novelchat.ui.theme.InkMuted
import com.novelchat.ui.theme.InkPrimary
import com.novelchat.ui.theme.InkSecondary
import com.novelchat.ui.theme.ObsidianCanvas
import com.novelchat.ui.theme.OnErrorContainer
import com.novelchat.ui.theme.OnSageContainer
import com.novelchat.ui.theme.PrimaryPillButton
import com.novelchat.ui.theme.SageContainer
import com.novelchat.ui.theme.SageTertiary
import com.novelchat.ui.theme.SecondaryPillButton
import com.novelchat.ui.theme.SegmentedPillOption
import com.novelchat.ui.theme.SegmentedPillTrack
import com.novelchat.ui.theme.SurfaceBase
import com.novelchat.ui.theme.SurfaceHigh
import com.novelchat.ui.theme.SurfaceLow
import com.novelchat.ui.theme.TonalBadge
import com.novelchat.ui.theme.novelTextFieldColors
import com.novelchat.ui.theme.rememberAdaptiveSpec
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
    val adaptive = rememberAdaptiveSpec()
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
                title = {
                    Text(
                        text = "导入故事",
                        color = InkPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, "返回", tint = InkSecondary)
                    }
                },
                actions = {
                    if (state.storyImportMode == StoryImportMode.ADVANCED_JSON) {
                        IconButton(onClick = { showGuide = true }) {
                            Icon(
                                Icons.AutoMirrored.Default.HelpOutline,
                                contentDescription = "高级接入指南",
                                tint = InkSecondary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianCanvas)
            )
        },
        containerColor = ObsidianCanvas
    ) { padding ->
        CenteredAdaptiveBox(
            maxWidth = adaptive.readingMaxWidth,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = adaptive.horizontalPadding, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            SegmentedPillTrack {
                SegmentedPillOption(
                    label = "AI 识别",
                    selected = state.storyImportMode == StoryImportMode.AI_RECOGNITION,
                    enabled = !state.isParsing,
                    icon = Icons.Default.AutoAwesome,
                    onClick = { viewModel.selectStoryImportMode(StoryImportMode.AI_RECOGNITION) },
                    modifier = Modifier.weight(1f)
                )
                SegmentedPillOption(
                    label = "高级接入",
                    selected = state.storyImportMode == StoryImportMode.ADVANCED_JSON,
                    enabled = !state.isParsing,
                    icon = Icons.Default.DataObject,
                    onClick = { viewModel.selectStoryImportMode(StoryImportMode.ADVANCED_JSON) },
                    modifier = Modifier.weight(1f)
                )
            }

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

            state.parseError?.let { error ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = ErrorContainer
                ) {
                    Text(
                        text = error,
                        color = OnErrorContainer,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }

            state.advancedImportSummary?.let { summary ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = SageContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = SageTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(summary, color = OnSageContainer, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }
            }

            PrimaryPillButton(
                onClick = {
                    when (state.storyImportMode) {
                        StoryImportMode.AI_RECOGNITION -> viewModel.parseStory()
                        StoryImportMode.ADVANCED_JSON -> viewModel.parseAdvancedStory()
                    }
                },
                enabled = !state.isParsing && when (state.storyImportMode) {
                    StoryImportMode.AI_RECOGNITION -> state.storyText.isNotBlank()
                    StoryImportMode.ADVANCED_JSON -> state.advancedStoryJson.isNotBlank()
                }
            ) {
                if (state.isParsing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = LocalContentColor.current,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = when {
                        state.isParsing -> "解析中..."
                        state.storyImportMode == StoryImportMode.ADVANCED_JSON -> "校验并生成预览"
                        else -> "解析故事设定"
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (state.parseResult != null) {
                SecondaryPillButton(onClick = onParsed) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("查看并确认全部设定", fontWeight = FontWeight.Medium)
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
private fun AiRecognitionEditor(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "输入自然语言设定，AI 将提取世界观、具名角色、开局场景与可检索世界书。",
            color = InkSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        ImportTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            placeholder = "例如：当代海港旧城，主角沈舟经营旧书铺……",
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
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TonalBadge(
                text = "JSON v${AdvancedStoryImportPolicy.SCHEMA_VERSION}",
                containerColor = SurfaceBase,
                contentColor = AmberPrimary
            )
            Spacer(Modifier.weight(1f))
            ToolbarActionChip(
                icon = Icons.Default.FolderOpen,
                label = "文件",
                contentDescription = "打开 JSON 文件",
                enabled = enabled,
                onClick = onOpenFile
            )
            ToolbarActionChip(
                icon = Icons.Default.ContentPaste,
                label = "粘贴",
                contentDescription = "从剪贴板粘贴",
                enabled = enabled,
                onClick = onPaste
            )
            ToolbarActionChip(
                icon = Icons.AutoMirrored.Default.NoteAdd,
                label = "模板",
                contentDescription = "载入完整模板",
                enabled = enabled,
                onClick = onLoadTemplate
            )
        }
        ImportTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            placeholder = "粘贴 JSON、打开文件，或点击上方载入模板",
            modifier = Modifier.weight(1f),
            monospace = true
        )
        Text(
            text = "${value.length} / ${AdvancedStoryImportPolicy.MAX_JSON_LENGTH}",
            color = InkMuted,
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = 6.dp)
        )
    }
}

@Composable
private fun ToolbarActionChip(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = SurfaceBase,
        border = BorderStroke(1.dp, Hairline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) InkSecondary else InkMuted,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                color = if (enabled) InkPrimary else InkMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
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
        placeholder = { Text(placeholder) },
        textStyle = TextStyle(
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            fontSize = if (monospace) 12.sp else 14.sp,
            lineHeight = if (monospace) 19.sp else 22.sp,
            color = InkPrimary
        ),
        colors = novelTextFieldColors(SurfaceBase),
        shape = RoundedCornerShape(18.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedImportGuideSheet(
    onDismiss: () -> Unit,
    onLoadTemplate: () -> Unit,
    onCopyTemplate: () -> Unit
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "高级接入指南",
                color = InkPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
            GuideSection("接入流程") {
                GuideText("准备 UTF-8 JSON → 打开文件或粘贴 → 校验 → 查看完整预览 → 确认创建。校验完全在本地完成，不消耗模型额度。")
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
            GuideSection("世界书与台词口吻") {
                GuideText("具体地点、组织、物品和事件应拆成 worldbook 条目，并填写关键词；NPC 支持在 lore 中填写 speech_style 与 sample_dialogues 代表台词。")
            }
            GuideSection("角色姓名") {
                GuideText("主角与所有会互动的 NPC 都必须有唯一真实姓名。身份写 occupation 或 role，例如姓名“王常”、职业“邻居”，不要把“邻居A”当姓名。")
            }
            GuideSection("秘密与可见性") {
                GuideText("人物幕后信息放 lore.secrets；世界资料使用 is_secret 与 known_by_player。秘密会提供给叙事模型保持一致，但正文仍应遵守角色知识边界。")
            }
            GuideSection("容量限制") {
                GuideText("最多 ${AdvancedStoryImportPolicy.MAX_NPCS} 位 NPC、${AdvancedStoryImportPolicy.MAX_WORLDBOOK_ENTRIES} 条世界书、${CharacterFieldPolicy.MAX_FIELDS} 个自定义角色字段、${AdvancedStoryImportPolicy.MAX_JSON_LENGTH} 个字符。custom_attributes 必须先在 character_fields 中定义。")
            }

            Spacer(Modifier.height(4.dp))
            PrimaryPillButton(onClick = onLoadTemplate) {
                Icon(Icons.AutoMirrored.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("载入完整模板", fontWeight = FontWeight.SemiBold)
            }
            SecondaryPillButton(onClick = onCopyTemplate) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("复制模板", fontWeight = FontWeight.Medium)
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("关闭", color = InkSecondary)
            }
        }
    }
}

@Composable
private fun GuideSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = AmberPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun GuideText(text: String) {
    Text(text, color = InkSecondary, fontSize = 13.sp, lineHeight = 20.sp)
}

@Composable
private fun GuideCode(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = SurfaceLow,
        border = BorderStroke(1.dp, Hairline)
    ) {
        Text(
            text = text,
            color = InkPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
        )
    }
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
