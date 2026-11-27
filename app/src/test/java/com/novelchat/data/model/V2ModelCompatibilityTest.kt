package com.novelchat.data.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V2ModelCompatibilityTest {

    private val gson = Gson()

    @Test
    fun legacyCharacterLoreAndCardJson_deserializeWithSafeV2Defaults() {
        val card = gson.fromJson(
            """{"id":"chen_jie","name":"房东陈姐","lore":{"speech_style":"直爽"}}""",
            CharacterCard::class.java
        )
        assertEquals(emptyList<String>(), card.lore.sampleDialogues)
        assertEquals(emptyList<MemoryNote>(), card.memories)
        assertTrue(card.lore.hasContent())

        val loreOnlySample = CharacterLore(sampleDialogues = listOf("周五交租。"))
        assertTrue(loreOnlySample.hasContent())
    }

    @Test
    fun legacyRoundCheckpointV1_deserializesWithProvisionalFalse() {
        val v1Json = """
            {
              "version": 1,
              "round": 3,
              "story_state": {"current_round": 2},
              "player_state": {"name": "林克"}
            }
        """.trimIndent()

        val checkpoint = gson.fromJson(v1Json, RoundCheckpoint::class.java)

        assertEquals(1, checkpoint.version)
        assertEquals(3, checkpoint.round)
        assertFalse(checkpoint.provisional)
        assertTrue(checkpoint.version in 1..RoundCheckpoint.CURRENT_VERSION)
    }

    @Test
    fun narrationSettings_normalizeAndResolveDefaults() {
        val raw = NarrationSettings(
            pov = "unknown",
            length = "DETAILED",
            styleSample = "  " + "字".repeat(900)
        ).normalized()

        assertEquals(NarrationPov.THIRD_LIMITED.storageKey, raw.pov)
        assertEquals(LengthPreference.DETAILED.storageKey, raw.length)
        assertEquals(NarrationSettings.MAX_STYLE_SAMPLE_LENGTH, raw.styleSample.length)
        assertEquals(LengthPreference.DETAILED, raw.resolvedLength(LengthPreference.SHORT))

        val empty = NarrationSettings().normalized()
        assertNull(empty.length)
        assertEquals(LengthPreference.STANDARD, empty.resolvedLength(LengthPreference.STANDARD))
    }

    @Test
    fun defaultStoryCharacters_includeSpeechStyleAndSampleDialogues() {
        val chars = defaultStoryCharacters("2026-11-27T00:00:00")
        assertEquals(24, chars.size)
        assertTrue(chars.all { it.lore.speechStyle.isNotBlank() })
        assertTrue(chars.all { it.lore.sampleDialogues.isNotEmpty() })
    }
}
