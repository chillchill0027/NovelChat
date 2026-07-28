package com.novelchat.ui.theme

import androidx.compose.ui.graphics.Color

// Reading-first dark palette with warm, cool and semantic accents.
val PrimaryPink = Color(0xFFE1B84B)     // warm narrative accent
val PrimaryPurple = Color(0xFFA59AF0)   // character/emotion accent
val DarkBg = Color(0xFF0E1116)          // near-black reading background
val PanelBg = Color(0xFF171C22)         // controls and context bands
val SurfaceLight = Color(0xFF28313A)    // elevated surface
val DimText = Color(0xFF929EAA)         // secondary text
val BrightText = Color(0xFFE7ECF1)      // primary text
val NeonCyan = Color(0xFF67C8B5)        // calm teal accent
val UserActionBg = Color(0xFF20313A)    // user action surface
val ContextBg = Color(0xFF13181E)       // compact scene context
val OutlineSoft = Color(0xFF313A44)

// Risk/status colors
val CvGreen = Color(0xFF5DCB8B)
val CvYellow = Color(0xFFE5C85B)
val CvOrange = Color(0xFFE99855)
val CvRed = Color(0xFFE76F78)

// NPC稀有度颜色
val RarityCommon = Color(0xFFB0BEC5)    // 普通：灰色
val RarityRare = Color(0xFF42A5F5)      // 稀有：蓝色
val RarityEpic = Color(0xFFAB47BC)      // 史诗：紫色
val RarityLegendary = Color(0xFFFFD700) // 传说：金色

// System accent colors
val SystemAIce = Color(0xFF00E5FF)
val SystemBTease = Color(0xFFFFB74D)
val SystemCMock = Color(0xFFFF1744)

// 进度条颜色
val BarPositive = Color(0xFF5DCB8B)     // 正面（如好感度>0）
val BarNegative = Color(0xFFE76F78)     // 负面（如好感度<0）
val BarNeutral = Color(0xFF75A9D6)      // 中性

// 兼容别名（老UI文件仍引用）
val GoldColor = PrimaryPink
val AccentRed = CvRed
