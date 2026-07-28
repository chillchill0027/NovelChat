package com.novelchat.ui.screen.lobby

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelchat.data.model.CharacterPreset
import com.novelchat.data.model.characterPresets
import com.novelchat.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetSelectScreen(
    onBack: () -> Unit,
    isCreating: Boolean = false,
    errorMessage: String? = null,
    onErrorShown: () -> Unit = {},
    onCustom: () -> Unit,
    onPresetSelected: (CharacterPreset) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
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
                title = { Text("选择开局身份", color = PrimaryPink) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isCreating) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, "返回", tint = DimText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = DarkBg
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            if (isCreating) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = PrimaryPink,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("正在创建故事...", color = DimText, fontSize = 12.sp)
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = onCustom,
                    enabled = !isCreating,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("自定义主角与开场")
                }
            }
            items(characterPresets) { preset ->
                val rankColor = when (preset.socialRank) {
                    "A", "S" -> RarityLegendary; "B", "C" -> RarityEpic; "D" -> RarityRare; else -> RarityCommon
                }
                val cvColor = when { preset.cv >= 15 -> CvRed; preset.cv >= 8 -> CvOrange; else -> CvGreen }
                Card(Modifier.fillMaxWidth().clickable(enabled = !isCreating) { onPresetSelected(preset) },
                    colors = CardDefaults.cardColors(containerColor = PanelBg), shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(preset.playerName, color = BrightText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text(preset.name, color = PrimaryPink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("Rank ${preset.socialRank}", color = rankColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(preset.description, color = DimText, fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            StatItem("魅力", "${preset.charm}", PrimaryPink)
                            StatItem("技巧", "${preset.huntingSkill}", PrimaryPink)
                            StatItem("意志", "${preset.alpha}", PrimaryPurple)
                            StatItem("风险", "${preset.cv}", cvColor)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(preset.systemEval, color = PrimaryPink, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = DimText, fontSize = 11.sp)
        Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
