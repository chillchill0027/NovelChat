package com.novelchat.data.storage

import com.novelchat.data.model.CharacterCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterCardFilePolicyTest {

    @Test
    fun fileStem_prefersStableIdOverDisplayName() {
        val card = CharacterCard(id = "chen_jie", name = "房东陈姐")

        assertEquals("chen_jie", CharacterCardFilePolicy.fileStem(card))
    }

    @Test
    fun safeFileName_replacesPathSeparatorsAndAddsHash() {
        val result = CharacterCardFilePolicy.safeFileName("客户/A")

        assertTrue(result.startsWith("客户_A_"))
        assertTrue(result.length > "客户_A_".length)
    }

    @Test
    fun safeFileName_usesFallbackForBlankValue() {
        assertEquals("unnamed", CharacterCardFilePolicy.safeFileName("   "))
    }
}
