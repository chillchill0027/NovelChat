package com.novelchat.data.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyDataMigratorTest {

    @Test
    fun containsLegacyText_detectsOldAdultPreviewPhrases() {
        val oldPreview = "听到自由职业者带着温柔的催促声，陈佳佳仿佛被那句“自己用手指填补你的空虚”这句话彻底点燃了。"

        assertTrue(LegacyDataMigrator.containsLegacyText(oldPreview))
    }

    @Test
    fun containsLegacyText_allowsModernLifeSimulationPreview() {
        val modernPreview = "林越的消息弹了出来，会议提前到了上午九点半。主角必须在通勤和准备材料之间做取舍。"

        assertFalse(LegacyDataMigrator.containsLegacyText(modernPreview))
    }

    @Test
    fun containsLegacyText_detectsInternalMigrationLabels() {
        assertTrue(
            LegacyDataMigrator.containsLegacyText(
                "旧版测试故事，已迁移为通用叙事开局。"
            )
        )
        assertTrue(LegacyDataMigrator.containsLegacyText("旧版测试记录，建议重新开始剧情"))
    }
}
