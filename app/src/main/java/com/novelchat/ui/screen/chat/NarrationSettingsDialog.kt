package com.novelchat.ui.screen.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelchat.data.model.CreativityLevel
import com.novelchat.data.model.LengthPreference
import com.novelchat.data.model.NarrationPov
import com.novelchat.data.model.NarrationSettings
import com.novelchat.ui.theme.InkMuted
import com.novelchat.ui.theme.InkPrimary
import com.novelchat.ui.theme.InkSecondary
import com.novelchat.ui.theme.NovelFieldShape
import com.novelchat.ui.theme.PrimaryPillButton
import com.novelchat.ui.theme.SegmentedPillOption
import com.novelchat.ui.theme.SegmentedPillTrack
import com.novelchat.ui.theme.SurfaceBase
import com.novelchat.ui.theme.SurfaceLow
import com.novelchat.ui.theme.novelTextFieldColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NarrationSettingsDialog(
    settings: NarrationSettings,
    defaultLength: LengthPreference,
    creativity: CreativityLevel,
    onSave: (NarrationSettings, CreativityLevel) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedPov by remember(settings) { mutableStateOf(settings.resolvedPov()) }
    var selectedLength by remember(settings) {
        mutableStateOf(LengthPreference.fromStorageKey(settings.length))
    }
    var selectedCreativity by remember(creativity) { mutableStateOf(creativity) }
    var styleSample by remember(settings) { mutableStateOf(settings.styleSample) }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "叙事设置",
                        color = InkPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "调整本故事的叙述视角、篇幅与语言风格",
                        color = InkSecondary,
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "关闭", tint = InkSecondary)
                }
            }

            SectionTitle("叙述视角")
            SegmentedPillTrack {
                NarrationPov.entries.forEach { pov ->
                    SegmentedPillOption(
                        label = pov.displayName,
                        selected = selectedPov == pov,
                        modifier = Modifier.weight(1f),
                        onClick = { selectedPov = pov }
                    )
                }
            }

            SectionTitle("单轮篇幅")
            SegmentedPillTrack {
                SegmentedPillOption(
                    label = "默认(${defaultLength.displayName})",
                    selected = selectedLength == null,
                    modifier = Modifier.weight(1.35f),
                    onClick = { selectedLength = null }
                )
                LengthPreference.entries.forEach { length ->
                    SegmentedPillOption(
                        label = length.displayName,
                        selected = selectedLength == length,
                        modifier = Modifier.weight(1f),
                        onClick = { selectedLength = length }
                    )
                }
            }

            SectionTitle("行文风格与创造力")
            SegmentedPillTrack {
                CreativityLevel.entries.forEach { level ->
                    SegmentedPillOption(
                        label = level.displayName,
                        selected = selectedCreativity == level,
                        modifier = Modifier.weight(1f),
                        onClick = { selectedCreativity = level }
                    )
                }
            }

            SectionTitle("文风参考示例（可选）")
            OutlinedTextField(
                value = styleSample,
                onValueChange = {
                    styleSample = it.take(NarrationSettings.MAX_STYLE_SAMPLE_LENGTH)
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                placeholder = {
                    Text(
                        text = "粘贴 1~2 段你偏爱的文风片段，仅学习语感、节奏与留白",
                        fontSize = 13.sp
                    )
                },
                colors = novelTextFieldColors(SurfaceLow),
                shape = NovelFieldShape
            )
            Text(
                text = "${styleSample.length} / ${NarrationSettings.MAX_STYLE_SAMPLE_LENGTH}",
                color = InkMuted,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End
            )

            Spacer(Modifier.height(4.dp))
            PrimaryPillButton(
                onClick = {
                    val updated = NarrationSettings(
                        pov = selectedPov.storageKey,
                        length = selectedLength?.storageKey,
                        styleSample = styleSample
                    ).normalized()
                    onSave(updated, selectedCreativity)
                }
            ) {
                Text("保存设置", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = InkSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold
    )
}
