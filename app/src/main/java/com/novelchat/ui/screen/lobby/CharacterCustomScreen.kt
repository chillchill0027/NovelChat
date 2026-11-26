package com.novelchat.ui.screen.lobby

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelchat.data.model.CharacterNamingPolicy
import com.novelchat.ui.theme.CenteredAdaptiveBox
import com.novelchat.ui.theme.CvRed
import com.novelchat.ui.theme.Hairline
import com.novelchat.ui.theme.InkPrimary
import com.novelchat.ui.theme.InkSecondary
import com.novelchat.ui.theme.NovelFieldShape
import com.novelchat.ui.theme.ObsidianCanvas
import com.novelchat.ui.theme.PrimaryPillButton
import com.novelchat.ui.theme.SectionOverline
import com.novelchat.ui.theme.SurfaceBase
import com.novelchat.ui.theme.novelTextFieldColors
import com.novelchat.ui.theme.rememberAdaptiveSpec

data class CustomCharacterDraft(
    val name: String,
    val age: Int,
    val gender: String,
    val occupation: String,
    val description: String,
    val personality: String,
    val appearance: String,
    val background: String,
    val openingPrompt: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterCustomScreen(
    onBack: () -> Unit,
    isCreating: Boolean = false,
    errorMessage: String? = null,
    onErrorShown: () -> Unit = {},
    onCreate: (CustomCharacterDraft) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var age by rememberSaveable { mutableStateOf("25") }
    var gender by rememberSaveable { mutableStateOf("") }
    var occupation by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var personality by rememberSaveable { mutableStateOf("") }
    var appearance by rememberSaveable { mutableStateOf("") }
    var background by rememberSaveable { mutableStateOf("") }
    var openingPrompt by rememberSaveable { mutableStateOf("") }
    val parsedAge = age.toIntOrNull()?.takeIf { it in 1..120 }
    val genericName = name.isNotBlank() && CharacterNamingPolicy.isGenericPlayerName(name)
    val snackbarHostState = remember { SnackbarHostState() }
    val adaptive = rememberAdaptiveSpec()

    BackHandler(enabled = isCreating) { }
    LaunchedEffect(errorMessage) {
        errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            onErrorShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "自定义开局",
                        color = InkPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isCreating) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, "返回", tint = InkSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianCanvas)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = adaptive.horizontalPadding, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            FormSectionCard("基础身份") {
                Field(
                    label = "角色姓名",
                    value = name,
                    onChange = { name = it.take(30) },
                    hint = "例如：许安",
                    enabled = !isCreating,
                    isError = genericName
                )
                if (genericName) {
                    Text("请填写角色在故事中的具体姓名", color = CvRed, fontSize = 12.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Field(
                        label = "年龄",
                        value = age,
                        onChange = { value -> age = value.filter(Char::isDigit).take(3) },
                        hint = "25",
                        enabled = !isCreating,
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                        isError = age.isNotBlank() && parsedAge == null
                    )
                    Field(
                        label = "性别",
                        value = gender,
                        onChange = { gender = it.take(12) },
                        hint = "例如：女",
                        enabled = !isCreating,
                        modifier = Modifier.weight(1f)
                    )
                }
                Field("职业或身份", occupation, { occupation = it.take(40) }, "例如：产品设计师", enabled = !isCreating)
                Field("身份概述", description, { description = it.take(200) }, "例如：刚搬到海临市，正在适应新工作", true, !isCreating)
            }

            FormSectionCard("人物画像") {
                Field("性格特征", personality, { personality = it.take(400) }, "角色稳定的性格与处事方式", true, !isCreating)
                Field("外貌描述", appearance, { appearance = it.take(400) }, "外貌、衣着和容易被记住的特征", true, !isCreating)
                Field("背景故事", background, { background = it.take(800) }, "重要经历、关系与当前处境", true, !isCreating)
            }

            FormSectionCard("开场情境") {
                Field("开场事件", openingPrompt, { openingPrompt = it.take(600) }, "例如：下班回家时发现门口放着一封没有署名的信", true, !isCreating)
            }

            Spacer(Modifier.height(4.dp))
            PrimaryPillButton(
                onClick = {
                    parsedAge?.let { validAge ->
                        onCreate(
                            CustomCharacterDraft(
                                name = name.trim(),
                                age = validAge,
                                gender = gender.trim(),
                                occupation = occupation.trim(),
                                description = description.trim(),
                                personality = personality.trim(),
                                appearance = appearance.trim(),
                                background = background.trim(),
                                openingPrompt = openingPrompt.trim()
                            )
                        )
                    }
                },
                enabled = name.isNotBlank() && !genericName && parsedAge != null && !isCreating
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = LocalContentColor.current,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = if (isCreating) "正在创建..." else "开始故事",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
            Spacer(Modifier.height(24.dp))
        }
        }
    }
}

@Composable
private fun FormSectionCard(
    title: String,
    content: @Composable () -> Unit
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
            SectionOverline(title)
            content()
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    multiLine: Boolean = false,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = if (isError) CvRed else InkSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(hint) },
            minLines = if (multiLine) 2 else 1,
            maxLines = if (multiLine) 5 else 1,
            enabled = enabled,
            isError = isError,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = novelTextFieldColors(),
            shape = NovelFieldShape
        )
    }
}
