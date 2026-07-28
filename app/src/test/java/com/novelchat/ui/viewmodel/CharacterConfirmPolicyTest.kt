package com.novelchat.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterConfirmPolicyTest {

    @Test
    fun wantsToStart_acceptsExplicitStartCommands() {
        assertTrue(CharacterConfirmPolicy.wantsToStart("开始剧情"))
        assertTrue(CharacterConfirmPolicy.wantsToStart("可以开始！"))
        assertTrue(CharacterConfirmPolicy.wantsToStart("进入剧情"))
        assertTrue(CharacterConfirmPolicy.wantsToStart("go"))
    }

    @Test
    fun wantsToStart_rejectsNegativeSentences() {
        assertFalse(CharacterConfirmPolicy.wantsToStart("我还不想开始，先改一下职业"))
        assertFalse(CharacterConfirmPolicy.wantsToStart("不要开始剧情"))
        assertFalse(CharacterConfirmPolicy.wantsToStart("先不开始"))
    }

    @Test
    fun wantsToStart_rejectsModificationTextThatMentionsStart() {
        assertFalse(CharacterConfirmPolicy.wantsToStart("开场先从地铁站开始，但角色还要改"))
        assertFalse(CharacterConfirmPolicy.wantsToStart("我想把开始地点改成公司楼下"))
    }

    @Test
    fun confirmationText_preservesParagraphsAndBoundsOutput() {
        val response = "第一段。\n\n第二段。\n\n\n" + "长".repeat(7_000)
        val normalized = CharacterConfirmPolicy.confirmationText(response, "本地确认文案")

        assertTrue(normalized.startsWith("第一段。\n\n第二段。\n\n"))
        assertEquals(CharacterConfirmPolicy.MAX_CONFIRMATION_LENGTH, normalized.length)
        assertTrue(normalized.endsWith("..."))
    }

    @Test
    fun confirmationText_usesFallbackAndModificationDraftIsBounded() {
        assertEquals(
            "本地确认文案",
            CharacterConfirmPolicy.confirmationText("  ", "本地确认文案")
        )
        assertEquals(
            CharacterConfirmPolicy.MAX_MODIFICATION_LENGTH,
            CharacterConfirmPolicy.normalizeModificationDraft("改".repeat(1_000)).length
        )
    }
}
