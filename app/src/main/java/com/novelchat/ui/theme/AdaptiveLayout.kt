package com.novelchat.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Material 3 window width size classes supporting phones (Xiaomi 17 Pro ~407dp),
 * tablet portrait / split-screen (Xiaomi Pad 6 Pro portrait 800dp / split ~636dp),
 * and tablet landscape (Xiaomi Pad 6 Pro landscape 1280dp).
 */
enum class WindowWidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED
}

@Immutable
data class AdaptiveSpec(
    val widthClass: WindowWidthClass,
    val screenWidthDp: Int,
    val screenHeightDp: Int
) {
    val isLandscape: Boolean
        get() = screenWidthDp > screenHeightDp

    val isTablet: Boolean
        get() = widthClass != WindowWidthClass.COMPACT

    /** Two-pane Chat layout (persistent Story Sidebar + centered Reading Pane). */
    val isTwoPaneChat: Boolean
        get() = widthClass == WindowWidthClass.EXPANDED && screenHeightDp >= 480

    /** Two-column form/settings/dossier layout on tablet landscape. */
    val isTwoColumnDetail: Boolean
        get() = widthClass == WindowWidthClass.EXPANDED && screenHeightDp >= 480

    /** Adaptive card grid columns for Lobby, Presets, Characters, and Worldbook. */
    val cardGridColumns: Int
        get() = when (widthClass) {
            WindowWidthClass.COMPACT -> 1
            WindowWidthClass.MEDIUM -> 2
            WindowWidthClass.EXPANDED -> if (screenWidthDp >= 1160) 2 else 2
        }

    /** Worldbook / compact card grid columns (3 columns on wide tablet landscape). */
    val denseGridColumns: Int
        get() = when (widthClass) {
            WindowWidthClass.COMPACT -> 1
            WindowWidthClass.MEDIUM -> 2
            WindowWidthClass.EXPANDED -> if (screenWidthDp >= 1120) 3 else 2
        }

    val horizontalPadding: Dp
        get() = when (widthClass) {
            WindowWidthClass.COMPACT -> 16.dp
            WindowWidthClass.MEDIUM -> 28.dp
            WindowWidthClass.EXPANDED -> 32.dp
        }

    val contentMaxWidth: Dp
        get() = when (widthClass) {
            WindowWidthClass.COMPACT -> Dp.Unspecified
            WindowWidthClass.MEDIUM -> 760.dp
            WindowWidthClass.EXPANDED -> 1140.dp
        }

    val formMaxWidth: Dp
        get() = when (widthClass) {
            WindowWidthClass.COMPACT -> Dp.Unspecified
            WindowWidthClass.MEDIUM -> 680.dp
            WindowWidthClass.EXPANDED -> 1080.dp
        }

    val readingMaxWidth: Dp
        get() = when (widthClass) {
            WindowWidthClass.COMPACT -> Dp.Unspecified
            WindowWidthClass.MEDIUM -> 720.dp
            WindowWidthClass.EXPANDED -> 780.dp
        }

    val sidebarWidth: Dp
        get() = when {
            screenWidthDp >= 1200 -> 360.dp
            else -> 324.dp
        }

    val proseFontSize: TextUnit
        get() = when (widthClass) {
            WindowWidthClass.COMPACT -> 16.sp
            WindowWidthClass.MEDIUM -> 16.5.sp
            WindowWidthClass.EXPANDED -> 17.sp
        }

    val proseLineHeight: TextUnit
        get() = when (widthClass) {
            WindowWidthClass.COMPACT -> 27.sp
            WindowWidthClass.MEDIUM -> 28.sp
            WindowWidthClass.EXPANDED -> 29.sp
        }

    val userBubbleMaxWidth: Dp
        get() = when (widthClass) {
            WindowWidthClass.COMPACT -> 310.dp
            WindowWidthClass.MEDIUM -> 420.dp
            WindowWidthClass.EXPANDED -> 460.dp
        }
}

object AdaptiveLayoutPolicy {
    fun compute(screenWidthDp: Int, screenHeightDp: Int): AdaptiveSpec {
        val widthClass = when {
            screenWidthDp >= 840 -> WindowWidthClass.EXPANDED
            screenWidthDp >= 600 -> WindowWidthClass.MEDIUM
            else -> WindowWidthClass.COMPACT
        }
        return AdaptiveSpec(
            widthClass = widthClass,
            screenWidthDp = screenWidthDp,
            screenHeightDp = screenHeightDp
        )
    }
}

@Composable
fun rememberAdaptiveSpec(): AdaptiveSpec {
    val config = LocalConfiguration.current
    return AdaptiveLayoutPolicy.compute(
        screenWidthDp = config.screenWidthDp,
        screenHeightDp = config.screenHeightDp
    )
}

/**
 * Centers content within an optimal maximum width on Medium/Expanded tablet screens,
 * while remaining full-width on Compact phones.
 */
@Composable
fun CenteredAdaptiveBox(
    maxWidth: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = if (maxWidth != Dp.Unspecified) {
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = maxWidth)
            } else {
                Modifier.fillMaxWidth()
            },
            content = content
        )
    }
}
