package com.novelchat.ui.screen.welcome

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelchat.data.model.CreativityLevel
import com.novelchat.data.model.LengthPreference
import com.novelchat.data.remote.AiCallStage
import com.novelchat.data.remote.model.ApiProfile
import com.novelchat.ui.theme.AmberContainer
import com.novelchat.ui.theme.AmberPrimary
import com.novelchat.ui.theme.CenteredAdaptiveBox
import com.novelchat.ui.theme.CvRed
import com.novelchat.ui.theme.ErrorContainer
import com.novelchat.ui.theme.Hairline
import com.novelchat.ui.theme.InkMuted
import com.novelchat.ui.theme.InkPrimary
import com.novelchat.ui.theme.InkSecondary
import com.novelchat.ui.theme.NovelFieldShape
import com.novelchat.ui.theme.ObsidianCanvas
import com.novelchat.ui.theme.OnAmberContainer
import com.novelchat.ui.theme.OnErrorContainer
import com.novelchat.ui.theme.OnSageContainer
import com.novelchat.ui.theme.PrimaryPillButton
import com.novelchat.ui.theme.SageContainer
import com.novelchat.ui.theme.SectionOverline
import com.novelchat.ui.theme.SegmentedPillOption
import com.novelchat.ui.theme.SegmentedPillTrack
import com.novelchat.ui.theme.SurfaceBase
import com.novelchat.ui.theme.SurfaceHigh
import com.novelchat.ui.theme.SurfaceLow
import com.novelchat.ui.theme.TonalBadge
import com.novelchat.ui.theme.novelTextFieldColors
import com.novelchat.ui.theme.rememberAdaptiveSpec
import com.novelchat.ui.viewmodel.SetupFlowPolicy
import com.novelchat.ui.viewmodel.SetupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiSetupScreen(
    viewModel: SetupViewModel,
    onBack: () -> Unit,
    onNext: () -> Unit,
    continueLabel: String = "下一步：导入故事"
) {
    val state by viewModel.state.collectAsState()
    var saveName by remember { mutableStateOf("") }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showRoutingDialog by rememberSaveable { mutableStateOf(false) }
    var pendingProfileDelete by remember { mutableStateOf<ApiProfile?>(null) }
    var apiKeyVisible by rememberSaveable { mutableStateOf(false) }
    val canContinue = SetupFlowPolicy.canContinue(state.apiUrl, state.apiKey, state.model)
    val configurationError = SetupFlowPolicy.validationError(state.apiUrl, state.apiKey, state.model)
    val hasSuccessfulTest = SetupFlowPolicy.hasSuccessfulTest(state.testResult)
    val isConnectionBusy = state.isTesting || state.isContinuing || state.isProfileMutating
    val assignedStageCount = stageRouteSpecs.count {
        state.apiStageRoutes.profileName(it.stage).isNotBlank()
    }
    val adaptive = rememberAdaptiveSpec()

    BackHandler(enabled = isConnectionBusy) {}

    LaunchedEffect(Unit) {
        viewModel.loadProfiles()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "API 连接配置",
                        color = InkPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isConnectionBusy) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, "返回", tint = InkSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianCanvas)
            )
        },
        containerColor = ObsidianCanvas
    ) { padding ->
        val profilesAndRoutingCard: @Composable () -> Unit = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = SurfaceBase,
                border = BorderStroke(1.dp, Hairline)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (state.profiles.isNotEmpty()) {
                        Column(
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SectionOverline("已保存的配置")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                state.profiles.forEach { profile ->
                                    val isSelected = profile.name == state.profileName
                                    Surface(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .clickable(enabled = !isConnectionBusy) {
                                                apiKeyVisible = false
                                                viewModel.selectProfile(profile)
                                            },
                                        shape = CircleShape,
                                        color = if (isSelected) AmberContainer else SurfaceHigh,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) AmberPrimary.copy(alpha = 0.6f) else Hairline
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = AmberPrimary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(Modifier.width(5.dp))
                                            }
                                            Text(
                                                text = profile.name,
                                                color = if (isSelected) OnAmberContainer else InkPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.widthIn(max = 150.dp)
                                            )
                                            IconButton(
                                                onClick = { pendingProfileDelete = profile },
                                                enabled = !isConnectionBusy,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "删除配置 ${profile.name}",
                                                    tint = if (isSelected) OnAmberContainer.copy(alpha = 0.8f) else InkMuted,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = Hairline)
                    }

                    val routingEnabled = state.profiles.isNotEmpty() && !isConnectionBusy
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = routingEnabled) { showRoutingDialog = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = if (routingEnabled) AmberPrimary else InkMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "高级调用路由",
                                color = if (routingEnabled) InkPrimary else InkSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (state.profiles.isEmpty()) "保存多个配置后可按阶段分流模型" else "为正文、状态同步与总结分别指定模型",
                                color = InkMuted,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        TonalBadge(
                            text = if (assignedStageCount == 0) "默认" else "已分配 $assignedStageCount",
                            containerColor = if (assignedStageCount == 0) SurfaceHigh else AmberContainer,
                            contentColor = if (assignedStageCount == 0) InkSecondary else OnAmberContainer
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = InkMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        val endpointFormCard: @Composable () -> Unit = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = SurfaceBase,
                border = BorderStroke(1.dp, Hairline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SectionOverline("模型接口")

                    FieldLabel("API 地址")
                    OutlinedTextField(
                        value = state.apiUrl,
                        onValueChange = { viewModel.updateApiUrl(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://api.deepseek.com") },
                        enabled = !isConnectionBusy,
                        singleLine = true,
                        shape = NovelFieldShape,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            autoCorrectEnabled = false
                        ),
                        colors = novelTextFieldColors()
                    )

                    FieldLabel("API 密钥（本地模型可留空）")
                    OutlinedTextField(
                        value = state.apiKey,
                        onValueChange = { viewModel.updateApiKey(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("sk-...") },
                        singleLine = true,
                        shape = NovelFieldShape,
                        visualTransformation = if (apiKeyVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        enabled = !isConnectionBusy,
                        trailingIcon = {
                            IconButton(
                                onClick = { apiKeyVisible = !apiKeyVisible },
                                enabled = !isConnectionBusy
                            ) {
                                Icon(
                                    imageVector = if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (apiKeyVisible) "隐藏密钥" else "显示密钥",
                                    tint = InkSecondary
                                )
                            }
                        },
                        colors = novelTextFieldColors()
                    )

                    FieldLabel("模型名称")
                    OutlinedTextField(
                        value = state.model,
                        onValueChange = { viewModel.updateModel(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("deepseek-v4-flash") },
                        enabled = !isConnectionBusy,
                        singleLine = true,
                        shape = NovelFieldShape,
                        colors = novelTextFieldColors()
                    )

                    configurationError?.let { error ->
                        Text(
                            text = error,
                            color = CvRed,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }

                    Spacer(Modifier.height(2.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.testConnection() },
                            enabled = !isConnectionBusy && canContinue,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 46.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AmberContainer,
                                contentColor = OnAmberContainer,
                                disabledContainerColor = SurfaceHigh,
                                disabledContentColor = InkMuted
                            )
                        ) {
                            if (state.isTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = LocalContentColor.current,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(6.dp))
                            } else {
                                Icon(
                                    Icons.Default.WifiTethering,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                text = if (state.isTesting) "测试中..." else "测试连接",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        OutlinedButton(
                            onClick = { showSaveDialog = true },
                            enabled = hasSuccessfulTest && !isConnectionBusy && configurationError == null,
                            modifier = Modifier.heightIn(min = 46.dp),
                            shape = CircleShape,
                            border = BorderStroke(
                                1.dp,
                                if (hasSuccessfulTest && !isConnectionBusy && configurationError == null) {
                                    Hairline
                                } else {
                                    Hairline.copy(alpha = 0.5f)
                                }
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = SurfaceLow,
                                contentColor = InkPrimary,
                                disabledContentColor = InkMuted
                            )
                        ) {
                            Icon(
                                Icons.Default.BookmarkAdd,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text("保存", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    state.testResult?.let { result ->
                        val isSuccess = result.startsWith("连接成功")
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSuccess) SageContainer else ErrorContainer
                        ) {
                            Text(
                                text = result,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                color = if (isSuccess) OnSageContainer else OnErrorContainer,
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                        }
                    } ?: run {
                        if (canContinue) {
                            Text(
                                text = "推荐先点击「测试连接」验证接口；也可以直接继续。",
                                color = InkMuted,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }
        }

        val continueAction: @Composable () -> Unit = {
            PrimaryPillButton(
                onClick = { viewModel.continueWithCurrentConfig(onNext) },
                enabled = canContinue && !isConnectionBusy
            ) {
                if (state.isContinuing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = LocalContentColor.current,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = if (state.isContinuing) "正在保存..." else continueLabel,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        CenteredAdaptiveBox(
            maxWidth = adaptive.formMaxWidth,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            if (adaptive.isTwoColumnDetail) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = adaptive.horizontalPadding, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1.1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        endpointFormCard()
                    }
                    Column(
                        modifier = Modifier.weight(0.9f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        profilesAndRoutingCard()
                        NarrationDefaultsCard(
                            narrationLength = state.narrationLength,
                            narrationCreativity = state.narrationCreativity,
                            onLengthSelected = viewModel::setNarrationLength,
                            onCreativitySelected = viewModel::setNarrationCreativity
                        )
                        continueAction()
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = adaptive.horizontalPadding, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    profilesAndRoutingCard()
                    endpointFormCard()
                    NarrationDefaultsCard(
                        narrationLength = state.narrationLength,
                        narrationCreativity = state.narrationCreativity,
                        onLengthSelected = viewModel::setNarrationLength,
                        onCreativitySelected = viewModel::setNarrationCreativity
                    )
                    Spacer(Modifier.height(4.dp))
                    continueAction()
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("保存配置档", color = InkPrimary, fontWeight = FontWeight.SemiBold) },
            text = {
                OutlinedTextField(
                    value = saveName,
                    onValueChange = { saveName = it.take(40) },
                    placeholder = { Text("例如：DeepSeek 官方") },
                    singleLine = true,
                    shape = NovelFieldShape,
                    colors = novelTextFieldColors(SurfaceBase)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (saveName.isNotBlank()) {
                            viewModel.saveCurrentProfile(saveName)
                            saveName = ""
                            showSaveDialog = false
                        }
                    },
                    enabled = saveName.isNotBlank()
                ) {
                    Text("保存", color = AmberPrimary, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("取消", color = InkSecondary)
                }
            },
            containerColor = SurfaceHigh,
            shape = RoundedCornerShape(28.dp)
        )
    }

    pendingProfileDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = {
                if (!isConnectionBusy) pendingProfileDelete = null
            },
            title = { Text("删除 API 配置？", color = InkPrimary, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    text = "将删除“${profile.name}”这条已保存配置，当前输入框内容不会被清空。",
                    color = InkSecondary,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isConnectionBusy,
                    onClick = {
                        pendingProfileDelete = null
                        viewModel.deleteProfile(profile.name)
                    }
                ) {
                    Text("删除", color = CvRed, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isConnectionBusy,
                    onClick = { pendingProfileDelete = null }
                ) {
                    Text("取消", color = InkSecondary)
                }
            },
            containerColor = SurfaceHigh,
            shape = RoundedCornerShape(28.dp)
        )
    }

    if (showRoutingDialog) {
        ApiStageRoutingDialog(
            profiles = state.profiles,
            routes = state.apiStageRoutes.routes,
            enabled = !isConnectionBusy,
            onSelect = viewModel::assignProfileToStage,
            onDismiss = {
                if (!isConnectionBusy) showRoutingDialog = false
            }
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        color = InkSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium
    )
}

private data class StageRouteSpec(
    val stage: AiCallStage,
    val title: String,
    val subtitle: String
)

private val stageRouteSpecs = listOf(
    StageRouteSpec(AiCallStage.STORY_IMPORT, "故事识别", "解析世界、角色与开局"),
    StageRouteSpec(AiCallStage.CHARACTER_SETUP, "角色设定", "确认与修改主角"),
    StageRouteSpec(AiCallStage.NARRATIVE, "剧情正文", "生成每轮主要叙事"),
    StageRouteSpec(AiCallStage.STATE_KEEPER, "状态同步", "后台状态更新与行动建议"),
    StageRouteSpec(AiCallStage.SUMMARIZER, "章节浓缩", "每10轮章节总结与长期记忆")
)

@Composable
private fun NarrationDefaultsCard(
    narrationLength: LengthPreference,
    narrationCreativity: CreativityLevel,
    onLengthSelected: (LengthPreference) -> Unit,
    onCreativitySelected: (CreativityLevel) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = SurfaceBase,
        border = BorderStroke(1.dp, Hairline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionOverline("默认叙事设置")

            FieldLabel("默认叙事篇幅")
            SegmentedPillTrack {
                LengthPreference.entries.forEach { length ->
                    SegmentedPillOption(
                        label = length.displayName,
                        selected = narrationLength == length,
                        onClick = { onLengthSelected(length) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            FieldLabel("默认创造力")
            SegmentedPillTrack {
                CreativityLevel.entries.forEach { level ->
                    SegmentedPillOption(
                        label = level.displayName,
                        selected = narrationCreativity == level,
                        onClick = { onCreativitySelected(level) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ApiStageRoutingDialog(
    profiles: List<ApiProfile>,
    routes: Map<String, String>,
    enabled: Boolean,
    onSelect: (AiCallStage, String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "高级调用路由",
                color = InkPrimary,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "未单独指定的环节将自动使用当前默认配置。",
                    color = InkSecondary,
                    fontSize = 12.sp
                )
                stageRouteSpecs.forEach { spec ->
                    StageProfileSelector(
                        spec = spec,
                        selectedProfileName = routes[spec.stage.storageKey].orEmpty(),
                        profiles = profiles,
                        enabled = enabled,
                        onSelect = { profileName -> onSelect(spec.stage, profileName) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = enabled) {
                Text("完成", color = AmberPrimary, fontWeight = FontWeight.SemiBold)
            }
        },
        containerColor = SurfaceHigh,
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
private fun StageProfileSelector(
    spec: StageRouteSpec,
    selectedProfileName: String,
    profiles: List<ApiProfile>,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = SurfaceLow,
        border = BorderStroke(1.dp, Hairline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(spec.title, color = InkPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(spec.subtitle, color = InkMuted, fontSize = 11.sp)
            }
            Spacer(Modifier.width(8.dp))
            Box {
                OutlinedButton(
                    onClick = { expanded = true },
                    enabled = enabled,
                    modifier = Modifier.widthIn(min = 112.dp, max = 160.dp),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, Hairline),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = InkPrimary)
                ) {
                    Text(
                        text = selectedProfileName.ifBlank { "默认配置" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(2.dp))
                    Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    containerColor = SurfaceBase
                ) {
                    DropdownMenuItem(
                        text = { Text("默认配置", color = InkPrimary) },
                        trailingIcon = {
                            if (selectedProfileName.isBlank()) {
                                Icon(Icons.Default.Check, null, tint = AmberPrimary)
                            }
                        },
                        onClick = {
                            expanded = false
                            onSelect("")
                        }
                    )
                    profiles.forEach { profile ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    profile.name,
                                    color = InkPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            trailingIcon = {
                                if (selectedProfileName == profile.name) {
                                    Icon(Icons.Default.Check, null, tint = AmberPrimary)
                                }
                            },
                            onClick = {
                                expanded = false
                                onSelect(profile.name)
                            }
                        )
                    }
                }
            }
        }
    }
}
