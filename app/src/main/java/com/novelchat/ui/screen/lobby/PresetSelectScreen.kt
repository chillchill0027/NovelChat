package com.novelchat.ui.screen.lobby

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelchat.data.model.CharacterPreset
import com.novelchat.data.model.characterPresets
import com.novelchat.ui.theme.AmberContainer
import com.novelchat.ui.theme.AmberPrimary
import com.novelchat.ui.theme.CenteredAdaptiveBox
import com.novelchat.ui.theme.CvGreen
import com.novelchat.ui.theme.CvOrange
import com.novelchat.ui.theme.CvRed
import com.novelchat.ui.theme.EditorialAvatar
import com.novelchat.ui.theme.Hairline
import com.novelchat.ui.theme.InkMuted
import com.novelchat.ui.theme.InkPrimary
import com.novelchat.ui.theme.InkSecondary
import com.novelchat.ui.theme.MistSecondary
import com.novelchat.ui.theme.ObsidianCanvas
import com.novelchat.ui.theme.OnAmberContainer
import com.novelchat.ui.theme.RarityCommon
import com.novelchat.ui.theme.RarityEpic
import com.novelchat.ui.theme.RarityLegendary
import com.novelchat.ui.theme.RarityRare
import com.novelchat.ui.theme.SectionOverline
import com.novelchat.ui.theme.SurfaceBase
import com.novelchat.ui.theme.SurfaceHigh
import com.novelchat.ui.theme.SurfaceLow
import com.novelchat.ui.theme.TonalBadge
import com.novelchat.ui.theme.rememberAdaptiveSpec

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
                        text = "选择开局身份",
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
            maxWidth = adaptive.contentMaxWidth,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val columns = adaptive.cardGridColumns
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = adaptive.horizontalPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 36.dp)
            ) {
                if (isCreating) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = SurfaceBase
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = AmberPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("正在创建故事档案...", color = InkSecondary, fontSize = 13.sp)
                            }
                        }
                    }
                }

                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable(enabled = !isCreating, onClick = onCustom),
                        shape = RoundedCornerShape(18.dp),
                        color = AmberContainer,
                        border = BorderStroke(1.dp, AmberPrimary.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = AmberPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "自定义主角与开场",
                                    color = OnAmberContainer,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "自由设定姓名、性格、背景与故事起点",
                                    color = OnAmberContainer.copy(alpha = 0.78f),
                                    fontSize = 12.sp
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = OnAmberContainer
                            )
                        }
                    }
                }

                item {
                    SectionOverline(
                        title = "推荐身份",
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                }

                if (columns > 1) {
                    val rows = characterPresets.chunked(columns)
                    items(rows, key = { row -> row.joinToString("-") { it.key } }) { rowPresets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowPresets.forEach { preset ->
                                Box(modifier = Modifier.weight(1f)) {
                                    PresetCard(
                                        preset = preset,
                                        enabled = !isCreating,
                                        onClick = { onPresetSelected(preset) }
                                    )
                                }
                            }
                            repeat(columns - rowPresets.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    items(characterPresets) { preset ->
                        PresetCard(
                            preset = preset,
                            enabled = !isCreating,
                            onClick = { onPresetSelected(preset) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetCard(
    preset: CharacterPreset,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val rankColor = when (preset.socialRank) {
        "A", "S" -> RarityLegendary
        "B", "C" -> RarityEpic
        "D" -> RarityRare
        else -> RarityCommon
    }
    val cvColor = when {
        preset.cv >= 15 -> CvRed
        preset.cv >= 8 -> CvOrange
        else -> CvGreen
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = SurfaceBase,
        border = BorderStroke(1.dp, Hairline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EditorialAvatar(
                    name = preset.playerName,
                    size = 44.dp,
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = preset.playerName,
                        color = InkPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = preset.name,
                        color = AmberPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                TonalBadge(
                    text = "阶层 ${preset.socialRank}",
                    containerColor = SurfaceHigh,
                    contentColor = rankColor
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = preset.description,
                color = InkSecondary,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = SurfaceLow
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem("魅力", "${preset.charm}", InkPrimary)
                    StatItem("技巧", "${preset.huntingSkill}", InkPrimary)
                    StatItem("意志", "${preset.alpha}", MistSecondary)
                    StatItem("风险", "${preset.cv}", cvColor)
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = preset.systemEval,
                color = InkMuted,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = InkMuted, fontSize = 11.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
