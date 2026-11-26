package com.novelchat.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Deterministic editorial monogram avatar used across Lobby, Chat HUD, and Character pages.
 */
@Composable
fun EditorialAvatar(
    name: String,
    size: Dp = 44.dp,
    shape: Shape = CircleShape,
    dimmed: Boolean = false,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = avatarPaletteFor(name)
    val initial = name.trim().take(1).ifBlank { "故" }
    val fontSize = when {
        size >= 52.dp -> 20.sp
        size >= 40.dp -> 16.sp
        size >= 32.dp -> 13.sp
        else -> 11.sp
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(if (dimmed) SurfaceHigh else bg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = if (dimmed) InkMuted else fg,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Quiet Material 3 section overline label with optional trailing caption/action.
 */
@Composable
fun SectionOverline(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = InkSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

/**
 * Compact tonal pill badge for metadata, tags, and status indicators.
 */
@Composable
fun TonalBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = SurfaceHigh,
    contentColor: Color = InkSecondary,
    icon: ImageVector? = null
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(12.dp)
                )
            }
            Text(
                text = text,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Material 3 segmented pill option used for mode/preference pickers.
 */
@Composable
fun SegmentedPillOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    Surface(
        modifier = modifier
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = if (selected) AmberContainer else Color.Transparent,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) AmberPrimary.copy(alpha = 0.55f) else Color.Transparent
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) OnAmberContainer else InkSecondary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text = label,
                color = when {
                    !enabled -> InkMuted
                    selected -> OnAmberContainer
                    else -> InkSecondary
                },
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Container pill housing a horizontal group of SegmentedPillOption items.
 */
@Composable
fun SegmentedPillTrack(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CircleShape,
        color = SurfaceLow,
        border = BorderStroke(1.dp, Hairline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/**
 * Standard Google-style full-pill primary action button (52dp height).
 */
@Composable
fun PrimaryPillButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = AmberPrimary,
            contentColor = OnAmberPrimary,
            disabledContainerColor = SurfaceHigh,
            disabledContentColor = InkMuted
        ),
        content = content
    )
}

/**
 * Standard Google-style full-pill secondary outlined/tonal action button.
 */
@Composable
fun SecondaryPillButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        shape = CircleShape,
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) Hairline.copy(alpha = 1f) else Hairline.copy(alpha = 0.6f)
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = SurfaceLow,
            contentColor = InkPrimary,
            disabledContainerColor = SurfaceLow.copy(alpha = 0.5f),
            disabledContentColor = InkMuted
        ),
        content = content
    )
}

/**
 * Cohesive Material 3 text field color palette across all setup, import, and editor forms.
 */
@Composable
fun novelTextFieldColors(containerColor: Color = SurfaceLow) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AmberPrimary.copy(alpha = 0.75f),
    unfocusedBorderColor = Hairline,
    disabledBorderColor = Hairline.copy(alpha = 0.5f),
    errorBorderColor = CvRed,
    focusedContainerColor = containerColor,
    unfocusedContainerColor = containerColor,
    disabledContainerColor = containerColor.copy(alpha = 0.6f),
    cursorColor = AmberPrimary,
    focusedTextColor = InkPrimary,
    unfocusedTextColor = InkPrimary,
    disabledTextColor = InkMuted,
    focusedPlaceholderColor = InkMuted,
    unfocusedPlaceholderColor = InkMuted
)

val NovelFieldShape = RoundedCornerShape(14.dp)
