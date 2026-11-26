package com.novelchat.ui.screen.welcome

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelchat.ui.theme.AmberContainer
import com.novelchat.ui.theme.AmberPrimary
import com.novelchat.ui.theme.CenteredAdaptiveBox
import com.novelchat.ui.theme.Hairline
import com.novelchat.ui.theme.InkMuted
import com.novelchat.ui.theme.InkPrimary
import com.novelchat.ui.theme.InkSecondary
import com.novelchat.ui.theme.MistContainer
import com.novelchat.ui.theme.MistSecondary
import com.novelchat.ui.theme.ObsidianCanvas
import com.novelchat.ui.theme.PrimaryPillButton
import com.novelchat.ui.theme.SageContainer
import com.novelchat.ui.theme.SageTertiary
import com.novelchat.ui.theme.SurfaceLow
import com.novelchat.ui.theme.rememberAdaptiveSpec

@Composable
fun WelcomeScreen(
    onApiSetup: () -> Unit
) {
    val adaptive = rememberAdaptiveSpec()
    val brandColumn: @Composable (Alignment.Horizontal) -> Unit = { alignment ->
        Column(horizontalAlignment = alignment) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = RoundedCornerShape(22.dp),
                color = AmberContainer,
                border = BorderStroke(1.dp, AmberPrimary.copy(alpha = 0.35f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = AmberPrimary,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            Text(
                text = "NovelChat",
                color = InkPrimary,
                fontSize = 36.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.5).sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "为你而写的 AI 互动叙事引擎",
                color = InkSecondary,
                fontSize = 15.sp,
                textAlign = if (alignment == Alignment.CenterHorizontally) TextAlign.Center else TextAlign.Start
            )
        }
    }
    val valueCard: @Composable () -> Unit = {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = SurfaceLow,
            border = BorderStroke(1.dp, Hairline)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                WelcomeFeatureRow(
                    icon = Icons.Default.AutoAwesome,
                    iconBg = AmberContainer,
                    iconTint = AmberPrimary,
                    title = "自然细腻的流式叙事",
                    subtitle = "纯文学正文即时呈现，告别机械模板与冗长等待"
                )
                WelcomeFeatureRow(
                    icon = Icons.Default.Bolt,
                    iconBg = SageContainer,
                    iconTint = SageTertiary,
                    title = "后台静默状态同步",
                    subtitle = "人物关系、物品与地点在后台自动演进，随时可继续互动"
                )
                WelcomeFeatureRow(
                    icon = Icons.Default.Psychology,
                    iconBg = MistContainer,
                    iconTint = MistSecondary,
                    title = "可定制世界书与长期记忆",
                    subtitle = "支持自然语言或结构化 JSON 导入，自动沉淀章节脉络"
                )
            }
        }
    }
    val ctaBlock: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PrimaryPillButton(onClick = onApiSetup) {
                Text(
                    text = "连接 API",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "支持 DeepSeek、OpenAI 兼容接口及局域网本地模型",
                color = InkMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianCanvas)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        AmberPrimary.copy(alpha = 0.12f),
                        MistSecondary.copy(alpha = 0.04f),
                        Color.Transparent
                    ),
                    radius = 1100f
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        if (adaptive.isTwoColumnDetail) {
            CenteredAdaptiveBox(maxWidth = 980.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 36.dp, vertical = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(0.95f),
                        verticalArrangement = Arrangement.spacedBy(28.dp)
                    ) {
                        brandColumn(Alignment.Start)
                        ctaBlock()
                    }
                    Box(modifier = Modifier.weight(1.05f)) {
                        valueCard()
                    }
                }
            }
        } else {
            CenteredAdaptiveBox(maxWidth = 540.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.weight(0.9f))
                    brandColumn(Alignment.CenterHorizontally)
                    Spacer(Modifier.height(36.dp))
                    valueCard()
                    Spacer(Modifier.weight(1.1f))
                    Spacer(Modifier.height(28.dp))
                    ctaBlock()
                }
            }
        }
    }
}

@Composable
private fun WelcomeFeatureRow(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = InkPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = subtitle,
                color = InkSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}
