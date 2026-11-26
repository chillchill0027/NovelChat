package com.novelchat.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = AmberPrimary,
    onPrimary = OnAmberPrimary,
    primaryContainer = AmberContainer,
    onPrimaryContainer = OnAmberContainer,
    secondary = MistSecondary,
    onSecondary = OnMistSecondary,
    secondaryContainer = MistContainer,
    onSecondaryContainer = OnMistContainer,
    tertiary = SageTertiary,
    onTertiary = OnSageTertiary,
    tertiaryContainer = SageContainer,
    onTertiaryContainer = OnSageContainer,
    background = ObsidianCanvas,
    onBackground = InkPrimary,
    surface = SurfaceBase,
    onSurface = InkPrimary,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = InkSecondary,
    surfaceContainerLowest = SurfaceLowest,
    surfaceContainerLow = SurfaceLow,
    surfaceContainer = SurfaceBase,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerHighest = SurfaceHighest,
    outline = InkMuted,
    outlineVariant = Hairline,
    error = CvRed,
    onError = OnAmberPrimary,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer
)

private val NovelChatShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
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
