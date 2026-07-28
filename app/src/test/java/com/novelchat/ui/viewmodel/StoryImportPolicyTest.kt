package com.novelchat.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoryImportPolicyTest {

    @Test
    fun validationError_requiresContentAndAcceptsNormalStory() {
        assertEquals("故事设定不能为空", StoryImportPolicy.validationError("  "))
        assertNull(StoryImportPolicy.validationError("林克搬进海临市，准备开始新工作。"))
    }

    @Test
    fun normalizeDraft_boundsLargePaste() {
        val normalized = StoryImportPolicy.normalizeDraft("设".repeat(20_000))

        assertEquals(StoryImportPolicy.MAX_STORY_TEXT_LENGTH, normalized.length)
        assertNull(StoryImportPolicy.validationError(normalized))
    }
}
