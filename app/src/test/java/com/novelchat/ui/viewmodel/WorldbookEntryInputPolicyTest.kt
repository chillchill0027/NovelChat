package com.novelchat.ui.viewmodel

import com.novelchat.data.model.WorldbookEntry
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldbookEntryInputPolicyTest {

    @Test
    fun validationError_acceptsUsefulEntryAndRejectsOversizedFields() {
        assertNull(
            WorldbookEntryInputPolicy.validationError(
                type = "location",
                name = "云桥里",
                description = "一座老小区",
                keywords = listOf("小区", "咖啡店"),
                status = "正常"
            )
        )
        assertEquals(
            "名称不能超过 80 个字符",
            WorldbookEntryInputPolicy.validationError(
                "location",
                "名".repeat(81),
                "",
                emptyList(),
                ""
            )
        )
        assertEquals(
            "关键词不能超过 24 个",
            WorldbookEntryInputPolicy.validationError(
                "event",
                "兑奖",
                "",
                List(25) { "关键词$it" },
                ""
            )
        )
    }

    @Test
    fun normalize_trimsDeduplicatesAndDefaultsStatus() {
        val normalized = WorldbookEntryInputPolicy.normalize(
            type = "event",
            name = "  兑奖  ",
            description = "  彩票店正在核验大奖。  ",
            keywords = listOf("大奖", " 大奖 ", "店员", ""),
            status = " "
        )

        assertEquals("兑奖", normalized.name)
        assertEquals("彩票店正在核验大奖。", normalized.description)
        assertEquals(listOf("大奖", "店员"), normalized.keywords)
        assertEquals("正常", normalized.status)
    }

    @Test
    fun duplicateNameError_ignoresCurrentEntryButBlocksAnotherEntry() {
        val entries = listOf(
            WorldbookEntry(id = "wb_1", name = "云桥里"),
            WorldbookEntry(id = "wb_2", name = "彩票店")
        )

        assertNull(WorldbookEntryInputPolicy.duplicateNameError("wb_1", "云桥里", entries))
        assertEquals(
            "已经存在同名世界书条目，请使用不同名称",
            WorldbookEntryInputPolicy.duplicateNameError("wb_2", " 云桥里 ", entries)
        )
    }

    @Test
    fun newEntryId_usesUuidAndDoesNotCollide() {
        val fixed = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        assertEquals("wb_123e4567-e89b-12d3-a456-426614174000", WorldbookEntryInputPolicy.newEntryId(fixed))
        assertNotEquals(WorldbookEntryInputPolicy.newEntryId(), WorldbookEntryInputPolicy.newEntryId())
        assertTrue(WorldbookEntryInputPolicy.newEntryId().startsWith("wb_"))
    }
}
