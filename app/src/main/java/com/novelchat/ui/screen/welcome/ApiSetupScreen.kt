package com.novelchat.ui.screen.welcome

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelchat.data.remote.AiCallStage
import com.novelchat.data.remote.model.ApiProfile
import com.novelchat.ui.theme.*
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
    val assignedStageCount = AiCallStage.entries.count {
        state.apiStageRoutes.profileName(it).isNotBlank()
    }

    BackHandler(enabled = isConnectionBusy) {}

    LaunchedEffect(Unit) {
        viewModel.loadProfiles()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("API 连接配置", color = PrimaryPink) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isConnectionBusy) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, "返回", tint = DimText)
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 已保存的配置列表
            if (state.profiles.isNotEmpty()) {
                Text("已保存的配置", color = DimText, fontSize = 13.sp)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.profiles.forEach { profile ->
                        val isSelected = profile.name == state.profileName
                        Card(
                            modifier = Modifier
                                .clickable(enabled = !isConnectionBusy) {
                                    apiKeyVisible = false
                                    viewModel.selectProfile(profile)
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) PrimaryPink.copy(alpha = 0.15f) else PanelBg
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 12.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    profile.name,
                                    color = if (isSelected) PrimaryPink else BrightText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 160.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                if (isSelected) {
                                    Icon(Icons.Default.Check, null, tint = PrimaryPink, modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = { pendingProfileDelete = profile },
                                    enabled = !isConnectionBusy,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Default.Close, "删除配置 ${profile.name}", tint = CvRed, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { showRoutingDialog = true },
                enabled = state.profiles.isNotEmpty() && !isConnectionBusy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = BrightText)
            ) {
                Icon(Icons.Default.Tune, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "高级调用路由",
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start
                )
                Text(
                    if (assignedStageCount == 0) "默认" else "已分配 $assignedStageCount",
                    color = if (assignedStageCount == 0) DimText else PrimaryPink,
                    fontSize = 12.sp
                )
            }

            HorizontalDivider(color = SurfaceLight)

            // 表单
            Text("API 地址", color = DimText, fontSize = 13.sp)
            OutlinedTextField(
                value = state.apiUrl,
                onValueChange = { viewModel.updateApiUrl(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://api.deepseek.com", color = DimText) },
                enabled = !isConnectionBusy,
                singleLine = true,
                colors = textFieldColors()
            )

            Text("API 密钥（本地模型可留空）", color = DimText, fontSize = 13.sp)
            OutlinedTextField(
                value = state.apiKey,
                onValueChange = { viewModel.updateApiKey(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("sk-...", color = DimText) },
                singleLine = true,
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
                            tint = DimText
                        )
                    }
                },
                colors = textFieldColors()
            )

            Text("模型名称", color = DimText, fontSize = 13.sp)
            OutlinedTextField(
                value = state.model,
                onValueChange = { viewModel.updateModel(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("deepseek-v4-flash", color = DimText) },
                enabled = !isConnectionBusy,
                singleLine = true,
                colors = textFieldColors()
            )

            configurationError?.let { error ->
                Text(
                    text = error,
                    color = CvRed,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 测试连接
                Button(
                    onClick = { viewModel.testConnection() },
                    enabled = !isConnectionBusy && canContinue,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPink)
                ) {
                    if (state.isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = LocalContentColor.current,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(if (state.isTesting) "测试中..." else "测试连接", fontSize = 13.sp)
                }

                // 保存配置
                Button(
                    onClick = { showSaveDialog = true },
                    enabled = hasSuccessfulTest && !isConnectionBusy && configurationError == null,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                ) {
                    Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("保存", fontSize = 13.sp)
                }
            }

            if (!hasSuccessfulTest && canContinue) {
                Text(
                    "可以先导入故事；连接测试推荐执行，解析或聊天时若连接失败会显示错误。",
                    color = DimText,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            // 测试结果
            state.testResult?.let { result ->
                val isSuccess = result.startsWith("连接成功")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSuccess) PrimaryPink.copy(alpha = 0.15f) else CvRed.copy(alpha = 0.15f)
                    )
                ) {
                    Text(
                        text = result,
                        modifier = Modifier.padding(12.dp),
                        color = if (isSuccess) PrimaryPink else CvRed,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { viewModel.continueWithCurrentConfig(onNext) },
                enabled = canContinue && !isConnectionBusy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPink)
            ) {
                if (state.isContinuing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = LocalContentColor.current,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (state.isContinuing) "正在保存..." else continueLabel)
            }
        }
    }

    // 保存配置对话框
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("保存配置", color = PrimaryPink) },
            text = {
                OutlinedTextField(
                    value = saveName,
                    onValueChange = { saveName = it.take(40) },
                    placeholder = { Text("例如：OpenAI-GPT4", color = DimText) },
                    singleLine = true,
                    colors = textFieldColors()
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
                    Text("保存", color = PrimaryPink)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("取消", color = DimText)
                }
            },
            containerColor = PanelBg
        )
    }

    pendingProfileDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = {
                if (!isConnectionBusy) pendingProfileDelete = null
            },
            title = { Text("删除 API 配置？", color = PrimaryPink) },
            text = { Text("将删除“${profile.name}”这条已保存配置。当前输入框内容不会被清空。", color = DimText) },
            confirmButton = {
                TextButton(
                    enabled = !isConnectionBusy,
                    onClick = {
                        pendingProfileDelete = null
                        viewModel.deleteProfile(profile.name)
                    }
                ) {
                    Text("删除", color = CvRed)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isConnectionBusy,
                    onClick = { pendingProfileDelete = null }
                ) {
                    Text("取消", color = DimText)
                }
            },
            containerColor = PanelBg,
            shape = RoundedCornerShape(8.dp)
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

private data class StageRouteSpec(
    val stage: AiCallStage,
    val title: String,
    val subtitle: String
)

private val stageRouteSpecs = listOf(
    StageRouteSpec(AiCallStage.STORY_IMPORT, "故事识别", "解析世界、角色与开局"),
    StageRouteSpec(AiCallStage.CHARACTER_SETUP, "角色设定", "确认与修改主角"),
    StageRouteSpec(AiCallStage.NARRATIVE, "剧情正文", "生成每轮主要叙事"),
    StageRouteSpec(AiCallStage.NARRATIVE_REPAIR, "剧情修订", "补全格式与质量重写"),
    StageRouteSpec(AiCallStage.TASK_ASSIGNMENT, "状态分析", "任务、世界与事件变化"),
    StageRouteSpec(AiCallStage.CHARACTER_STATE, "角色刷新", "更新 NPC 角色卡")
)

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
        title = { Text("高级调用路由", color = PrimaryPink) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "未指定的环节使用当前默认配置。",
                    color = DimText,
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
                Text("完成", color = PrimaryPink)
            }
        },
        containerColor = PanelBg,
        shape = RoundedCornerShape(8.dp)
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(spec.title, color = BrightText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(spec.subtitle, color = DimText, fontSize = 11.sp)
            }
            Box {
                OutlinedButton(
                    onClick = { expanded = true },
                    enabled = enabled,
                    modifier = Modifier.widthIn(min = 132.dp, max = 180.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        selectedProfileName.ifBlank { "默认配置" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    containerColor = PanelBg
                ) {
                    DropdownMenuItem(
                        text = { Text("默认配置", color = BrightText) },
                        trailingIcon = {
                            if (selectedProfileName.isBlank()) {
                                Icon(Icons.Default.Check, null, tint = PrimaryPink)
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
                                    color = BrightText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            trailingIcon = {
                                if (selectedProfileName == profile.name) {
                                    Icon(Icons.Default.Check, null, tint = PrimaryPink)
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
        HorizontalDivider(color = SurfaceLight.copy(alpha = 0.7f))
    }
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PrimaryPink.copy(alpha = 0.5f),
    unfocusedBorderColor = SurfaceLight,
    focusedContainerColor = PanelBg,
    unfocusedContainerColor = PanelBg,
    cursorColor = PrimaryPink,
    focusedTextColor = BrightText,
    unfocusedTextColor = BrightText
)
