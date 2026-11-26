package com.novelchat.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLayoutPolicyTest {

    @Test
    fun xiaomi17ProPortrait_usesCompactPhoneSpec() {
        val spec = AdaptiveLayoutPolicy.compute(screenWidthDp = 407, screenHeightDp = 885)

        assertEquals(WindowWidthClass.COMPACT, spec.widthClass)
        assertFalse(spec.isTablet)
        assertFalse(spec.isTwoPaneChat)
        assertEquals(1, spec.cardGridColumns)
        assertEquals(1, spec.denseGridColumns)
    }

    @Test
    fun xiaomiPad6ProPortrait_usesMediumCenteredTwoColumnSpec() {
        // 1800x2880 @ 360dpi -> 800dp x 1280dp
        val spec = AdaptiveLayoutPolicy.compute(screenWidthDp = 800, screenHeightDp = 1280)

        assertEquals(WindowWidthClass.MEDIUM, spec.widthClass)
        assertTrue(spec.isTablet)
        assertFalse(spec.isTwoPaneChat)
        assertFalse(spec.isTwoColumnDetail)
        assertEquals(2, spec.cardGridColumns)
        assertEquals(2, spec.denseGridColumns)
    }

    @Test
    fun xiaomiPad6ProLandscape_usesExpandedTwoPaneSpec() {
        // 2880x1800 @ 360dpi -> 1280dp x 800dp
        val spec = AdaptiveLayoutPolicy.compute(screenWidthDp = 1280, screenHeightDp = 800)

        assertEquals(WindowWidthClass.EXPANDED, spec.widthClass)
        assertTrue(spec.isTablet)
        assertTrue(spec.isLandscape)
        assertTrue(spec.isTwoPaneChat)
        assertTrue(spec.isTwoColumnDetail)
        assertEquals(2, spec.cardGridColumns)
        assertEquals(3, spec.denseGridColumns)
    }

    @Test
    fun xiaomiPad6ProSplitScreenAndFloatingWindow_adaptGracefully() {
        val split = AdaptiveLayoutPolicy.compute(screenWidthDp = 636, screenHeightDp = 800)
        assertEquals(WindowWidthClass.MEDIUM, split.widthClass)
        assertFalse(split.isTwoPaneChat)
        assertEquals(2, split.cardGridColumns)

        val floating = AdaptiveLayoutPolicy.compute(screenWidthDp = 400, screenHeightDp = 640)
        assertEquals(WindowWidthClass.COMPACT, floating.widthClass)
        assertFalse(floating.isTablet)
        assertEquals(1, floating.cardGridColumns)
    }
}
