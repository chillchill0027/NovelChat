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
    var pendingProfileDelete by remember { mutableStateOf<ApiProfile?>(null) }
    var apiKeyVisible by rememberSaveable { mutableStateOf(false) }
    val canContinue = SetupFlowPolicy.canContinue(state.apiUrl, state.apiKey, state.model)
    val configurationError = SetupFlowPolicy.validationError(state.apiUrl, state.apiKey, state.model)
    val hasSuccessfulTest = SetupFlowPolicy.hasSuccessfulTest(state.testResult)
    val isConnectionBusy = state.isTesting || state.isContinuing || state.isProfileMutating

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
