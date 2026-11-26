package com.novelchat.ui.theme

import androidx.compose.ui.graphics.Color

// =============================================================================
// Material 3 Expressive Editorial Dark Palette — "Midnight Ink & Warm Lamp"
// Designed for OLED long-form Chinese narrative reading (zero cold-blue glare).
// =============================================================================

// Surface elevation ladder (tonal depth replaces harsh borders)
val ObsidianCanvas = Color(0xFF0E1014)      // App background & reading canvas
val SurfaceLowest = Color(0xFF090B0E)       // Deep recessed wells
val SurfaceLow = Color(0xFF15181F)          // Secondary panels, sheets, HUD capsules
val SurfaceBase = Color(0xFF1B1F28)         // Primary cards & containers
val SurfaceHigh = Color(0xFF232834)         // Elevated composer, dialogs, active controls
val SurfaceHighest = Color(0xFF2D3342)      // Prominent tonal chips & pressed states

// Brand & editorial accents (tonal pairs)
val AmberPrimary = Color(0xFFE7C278)        // Warm champagne gold — primary CTA & focus
val OnAmberPrimary = Color(0xFF231904)      // Deep ink on primary CTA
val AmberContainer = Color(0xFF362B16)      // Subtle warm highlight surface
val OnAmberContainer = Color(0xFFFBE2AC)    // Readable warm tint on AmberContainer

val MistSecondary = Color(0xFFB5C1E3)       // Calm editorial periwinkle — secondary highlights
val OnMistSecondary = Color(0xFF1B2338)
val MistContainer = Color(0xFF252B3D)       // Tonal secondary container
val OnMistContainer = Color(0xFFDCE4FA)

val SageTertiary = Color(0xFF7AD0B8)        // Calm jade — live status, sync, positive cues
val OnSageTertiary = Color(0xFF0A2720)
val SageContainer = Color(0xFF17332C)
val OnSageContainer = Color(0xFFB6F0DF)

// Ink / Typography hierarchy (warm ivory for reading comfort on OLED)
val InkPrimary = Color(0xFFECEAE4)          // Reading prose & primary titles
val InkSecondary = Color(0xFFA0A8B6)        // Metadata, subtitles, secondary labels
val InkMuted = Color(0xFF6E7686)            // Timestamps, subtle dividers, tertiary captions
val Hairline = Color(0xFF292F3C)            // Quiet subtle borders & hairlines

// Chat reading surfaces
val UserBubbleBg = Color(0xFF222A3A)        // Calm indigo-slate tonal user bubble
val UserBubbleBorder = Color(0xFF333F56)    // Gentle crisp edge on user bubble
val ProseAccentBar = Color(0xFF54472C)      // Quiet editorial margin rule (never loud)

// Semantic status colors (pastel-calibrated for dark OLED legibility)
val CvGreen = Color(0xFF6ED49B)
val CvYellow = Color(0xFFEAC86B)
val CvOrange = Color(0xFFEE9F62)
val CvRed = Color(0xFFF07B84)
val ErrorContainer = Color(0xFF3B1B20)
val OnErrorContainer = Color(0xFFFFD9DC)

// Rank / Rarity accents (refined editorial tones)
val RarityCommon = Color(0xFFA3B0BE)
val RarityRare = Color(0xFF7CB4F8)
val RarityEpic = Color(0xFFC29EF8)
val RarityLegendary = Color(0xFFEBC86E)

// Directional indicator colors
val BarPositive = CvGreen
val BarNegative = CvRed
val BarNeutral = Color(0xFF83B1DE)

// Deterministic avatar tonal pairs (bg, fg) for character & story monograms
val AvatarPalettes: List<Pair<Color, Color>> = listOf(
    Color(0xFF352B19) to Color(0xFFF0CE86), // Warm Amber
    Color(0xFF19322D) to Color(0xFF84D9C0), // Soft Jade
    Color(0xFF232A40) to Color(0xFFAEBEF0), // Dusk Periwinkle
    Color(0xFF34222D) to Color(0xFFE8A4C4), // Dusty Rose
    Color(0xFF2D253C) to Color(0xFFCBB0F5), // Muted Lavender
    Color(0xFF222F3A) to Color(0xFF8CC5EA)  // Mist Slate Blue
)

fun avatarPaletteFor(seed: String): Pair<Color, Color> {
    if (seed.isBlank()) return AvatarPalettes.first()
    val idx = (seed.hashCode() and 0x7fffffff) % AvatarPalettes.size
    return AvatarPalettes[idx]
}

// =============================================================================
// Legacy token aliases (kept for compatibility across modules)
// =============================================================================
val PrimaryPink = AmberPrimary
val PrimaryPurple = MistSecondary
val DarkBg = ObsidianCanvas
val PanelBg = SurfaceBase
val SurfaceLight = SurfaceHigh
val DimText = InkSecondary
val BrightText = InkPrimary
val NeonCyan = SageTertiary
val UserActionBg = UserBubbleBg
val ContextBg = SurfaceLow
val OutlineSoft = Hairline
val GoldColor = AmberPrimary
val AccentRed = CvRed
