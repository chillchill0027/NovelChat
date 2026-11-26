package com.novelchat.ui.screen.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelchat.data.remote.llm.LlmCallMetric
import com.novelchat.data.remote.llm.RoundPerfSummary
import com.novelchat.domain.v2.context.ContextDebugInfo
import com.novelchat.ui.theme.AmberPrimary
import com.novelchat.ui.theme.CvGreen
import com.novelchat.ui.theme.CvRed
import com.novelchat.ui.theme.CvYellow
import com.novelchat.ui.theme.Hairline
import com.novelchat.ui.theme.InkMuted
import com.novelchat.ui.theme.InkPrimary
import com.novelchat.ui.theme.InkSecondary
import com.novelchat.ui.theme.SageTertiary
import com.novelchat.ui.theme.SurfaceBase
import com.novelchat.ui.theme.SurfaceLow
import com.novelchat.ui.viewmodel.PerfDebugSnapshot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerfDebugSheet(
    snapshot: PerfDebugSnapshot,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
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
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "性能与上下文调试",
                    color = InkPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, "刷新", tint = SageTertiary)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "关闭", tint = InkSecondary)
                }
            }
            HorizontalDivider(color = Hairline)

            LazyColumn(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    SyncSummaryCard(
                        syncingRounds = snapshot.syncingRounds,
                        failedRounds = snapshot.failedRounds
                    )
                }
                snapshot.lastContextDebug?.let { debug ->
                    item { ContextDebugCard(debug) }
                }
                if (snapshot.recentRounds.isNotEmpty()) {
                    item { PerfSectionHeader("最近轮次 (${snapshot.recentRounds.size})") }
                    items(snapshot.recentRounds) { round ->
                        RoundPerfRow(round)
                    }
                }
                if (snapshot.recentCalls.isNotEmpty()) {
                    item { PerfSectionHeader("最近模型调用 (${snapshot.recentCalls.size})") }
                    items(snapshot.recentCalls) { call ->
                        CallMetricRow(call)
                    }
                }
                if (
                    snapshot.lastContextDebug == null &&
                    snapshot.recentRounds.isEmpty() &&
                    snapshot.recentCalls.isEmpty()
                ) {
                    item {
                        Text(
                            text = "暂无调用指标，请先发送至少一轮行动。",
                            color = InkSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PerfSectionHeader(title: String) {
    Text(
        text = title,
        color = AmberPrimary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
private fun SyncSummaryCard(syncingRounds: Set<Int>, failedRounds: Set<Int>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceLow,
        border = BorderStroke(1.dp, Hairline)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Text("后台同步队列", color = InkPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "进行中轮次：${syncingRounds.sorted().ifEmpty { listOf("无") }.joinToString(", ")}",
                color = if (syncingRounds.isEmpty()) InkSecondary else SageTertiary,
                fontSize = 12.sp
            )
            Text(
                text = "待补同步轮次：${failedRounds.sorted().ifEmpty { listOf("无") }.joinToString(", ")}",
                color = if (failedRounds.isEmpty()) InkSecondary else CvYellow,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ContextDebugCard(debug: ContextDebugInfo) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceLow,
        border = BorderStroke(1.dp, Hairline)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("上一轮提示词装配", color = InkPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = "system=${debug.systemChars}字 · 章节=${debug.chapterChars}字 · 历史=${debug.historyChars}字(${debug.historyTurnsIncluded}轮) · 本轮上下文=${debug.contextChars}字",
                color = SageTertiary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "在场=${debug.presentNpcs.ifEmpty { listOf("无") }.joinToString("、")} · 相关=${debug.relatedNpcs.ifEmpty { listOf("无") }.joinToString("、")}",
                color = InkSecondary,
                fontSize = 12.sp
            )
            Text(
                text = "世界书命中=${debug.worldbookNames.ifEmpty { listOf("无") }.joinToString("、")}",
                color = InkSecondary,
                fontSize = 12.sp
            )
            if (debug.trimmedItems.isNotEmpty()) {
                Text(
                    text = "预算裁剪：${debug.trimmedItems.joinToString("、")}",
                    color = CvYellow,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun RoundPerfRow(round: RoundPerfSummary) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = SurfaceLow,
        border = BorderStroke(1.dp, Hairline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("第${round.round}轮", color = InkPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(10.dp))
            Text(
                text = "阻塞=${round.blockingMs}ms · 调用=${round.calls}次",
                color = SageTertiary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
            round.syncMs?.let { syncMs ->
                Text("同步=${syncMs}ms", color = InkMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun CallMetricRow(call: LlmCallMetric) {
    val outcomeColor = when (call.outcome) {
        "ok" -> CvGreen
        "canceled" -> CvYellow
        else -> CvRed
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = SurfaceLow,
        border = BorderStroke(1.dp, Hairline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${call.stage.storageKey} · ${call.model}",
                    color = InkPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${call.outcome} #${call.attempt}",
                    color = outcomeColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = "ttft=${call.ttftMs ?: -1}ms · total=${call.totalMs}ms · in=${call.promptTokens}(cache=${call.cachedTokens}) · out=${call.completionTokens} · finish=${call.finishReason ?: "-"}",
                color = InkMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
