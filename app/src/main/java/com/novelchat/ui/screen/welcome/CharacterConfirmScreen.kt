package com.novelchat.ui.screen.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelchat.ui.theme.*
import com.novelchat.ui.viewmodel.CharacterConfirmPolicy
import com.novelchat.ui.viewmodel.CharacterConfirmViewModel

/**
 * 角色确认对话（对齐设计文档 11.5 节）
 * AI 生成角色描述 → 用户确认或修改 → 用户回复"开始剧情" → 进入正式剧情
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterConfirmScreen(
    characterId: Long,
    viewModel: CharacterConfirmViewModel,
    onBack: () -> Unit,
    onStartStory: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var inputText by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(characterId) {
        viewModel.initialize(characterId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("角色确认", color = GoldColor) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
        ) {
            // AI 角色确认消息
            Card(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = PanelBg),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    if (state.isLoading) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = GoldColor)
                        }
                    } else {
                        Text(
                            text = state.confirmationMessage,
                            color = GoldColor,
                            fontSize = 14.sp,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 错误提示
            state.errorMessage?.let { err ->
                Text(err, color = AccentRed, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            }

            Button(
                onClick = onStartStory,
                enabled = !state.isLoading && inputText.isBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GoldColor)
            ) {
                Text("开始故事", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = CharacterConfirmPolicy.normalizeModificationDraft(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("需要调整？直接描述修改内容", color = DimText) },
                enabled = !state.isLoading,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldColor.copy(alpha = 0.5f),
                    unfocusedBorderColor = SurfaceLight,
                    focusedContainerColor = PanelBg,
                    unfocusedContainerColor = PanelBg,
                    cursorColor = GoldColor,
                    focusedTextColor = GoldColor,
                    unfocusedTextColor = GoldColor
                ),
                shape = RoundedCornerShape(8.dp),
                minLines = 2,
                maxLines = 4
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    if (CharacterConfirmPolicy.wantsToStart(inputText)) {
                        onStartStory()
                    } else if (inputText.isNotBlank()) {
                        viewModel.confirmModify(inputText)
                        inputText = ""
                    }
                },
                enabled = inputText.isNotBlank() && !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("提交修改")
            }
        }
    }
}
