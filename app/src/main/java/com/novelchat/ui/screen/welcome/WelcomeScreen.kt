package com.novelchat.ui.screen.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelchat.ui.theme.*

@Composable
fun WelcomeScreen(
    onApiSetup: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(DarkBg), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp)
        ) {
            Text("NovelChat", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = PrimaryPink)
            Spacer(Modifier.height(4.dp))
            Text("AI 驱动的互动叙事引擎", fontSize = 13.sp, color = DimText)
            Spacer(Modifier.height(48.dp))

            Button(
                onClick = onApiSetup,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPink)
            ) {
                Text("连接 API", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Text("使用前请先配置 AI 模型接口", color = DimText, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}
