package com.novelchat.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryPink,
    secondary = PrimaryPurple,
    tertiary = NeonCyan,
    background = DarkBg,
    surface = PanelBg,
    onPrimary = DarkBg,
    onSecondary = DarkBg,
    onTertiary = DarkBg,
    onBackground = BrightText,
    onSurface = BrightText,
    outline = SurfaceLight,
    error = CvRed,
    onError = DarkBg
)

private val NovelChatShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp)
)

@Composable
fun NovelChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = NovelChatTypography,
        shapes = NovelChatShapes,
        content = content
    )
}
