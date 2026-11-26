package com.novelchat.ui.screen.welcome

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelchat.ui.theme.AmberPrimary
import com.novelchat.ui.theme.CenteredAdaptiveBox
import com.novelchat.ui.theme.ErrorContainer
import com.novelchat.ui.theme.Hairline
import com.novelchat.ui.theme.InkPrimary
import com.novelchat.ui.theme.InkSecondary
import com.novelchat.ui.theme.NovelFieldShape
import com.novelchat.ui.theme.ObsidianCanvas
import com.novelchat.ui.theme.OnErrorContainer
import com.novelchat.ui.theme.PrimaryPillButton
import com.novelchat.ui.theme.SecondaryPillButton
import com.novelchat.ui.theme.SectionOverline
import com.novelchat.ui.theme.SurfaceBase
import com.novelchat.ui.theme.novelTextFieldColors
import com.novelchat.ui.theme.rememberAdaptiveSpec
import com.novelchat.ui.viewmodel.CharacterConfirmPolicy
import com.novelchat.ui.viewmodel.CharacterConfirmViewModel

/**
 * 角色确认对话：展示主角档案 → 确认直接进入正式剧情，或提交自然语言修改。
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
    val adaptive = rememberAdaptiveSpec()

    LaunchedEffect(characterId) {
        viewModel.initialize(characterId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "角色确认",
                        color = InkPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, "返回", tint = InkSecondary)
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
                    .padding(horizontal = adaptive.horizontalPadding, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = SurfaceBase,
                border = BorderStroke(1.dp, Hairline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    SectionOverline("主角初始设定", modifier = Modifier.padding(bottom = 12.dp))
                    if (state.isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = AmberPrimary, strokeWidth = 2.5.dp)
                        }
                    } else {
                        Text(
                            text = state.confirmationMessage,
                            color = InkPrimary,
                            fontSize = 15.sp,
                            lineHeight = 25.sp
                        )
                    }
                }
            }

            state.errorMessage?.let { err ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = ErrorContainer
                ) {
                    Text(
                        text = err,
                        color = OnErrorContainer,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }

            PrimaryPillButton(
                onClick = onStartStory,
                enabled = !state.isLoading && inputText.isBlank()
            ) {
                Text("开始故事", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = CharacterConfirmPolicy.normalizeModificationDraft(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("需要微调主角？直接描述修改要求") },
                enabled = !state.isLoading,
                colors = novelTextFieldColors(SurfaceBase),
                shape = NovelFieldShape,
                minLines = 2,
                maxLines = 4
            )

            val canModify = inputText.isNotBlank() && !state.isLoading
            SecondaryPillButton(
                onClick = {
                    if (CharacterConfirmPolicy.wantsToStart(inputText)) {
                        onStartStory()
                    } else if (inputText.isNotBlank()) {
                        viewModel.confirmModify(inputText)
                        inputText = ""
                    }
                },
                enabled = canModify
            ) {
                Text("提交修改", fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(4.dp))
        }
        }
    }
}
