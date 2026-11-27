package com.novelchat.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterNamingPolicyTest {

    @Test
    fun concretePlayerName_replacesRoleLabelsButKeepsRealName() {
        assertEquals("林克", CharacterNamingPolicy.concretePlayerName("主角"))
        assertEquals("许安", CharacterNamingPolicy.concretePlayerName(" 许安 "))
        assertTrue(CharacterNamingPolicy.isGenericPlayerName("player"))
    }

    @Test
    fun normalizeNpcCard_replacesGenericNeighborLabelWithConcreteName() {
        val normalized = CharacterNamingPolicy.normalizeNpcCard(CharacterCard(name = "邻居A"))

        assertEquals("王常", normalized.name)
        assertEquals("邻居", normalized.occupation)
        assertTrue(normalized.background.contains("邻居"))
    }

    @Test
    fun normalizeNpcCard_keepsDifferentPlaceholderCharactersDistinct() {
        val first = CharacterNamingPolicy.normalizeNpcCard(CharacterCard(name = "邻居A"))
        val second = CharacterNamingPolicy.normalizeNpcCard(CharacterCard(name = "邻居B"))
        val secondAgain = CharacterNamingPolicy.normalizeNpcCard(CharacterCard(name = "邻居B"))

        assertEquals("王常", first.name)
        assertEquals("李雯", second.name)
        assertEquals(second.name, secondAgain.name)
    }

    @Test
    fun normalizeNpcCard_usesStableIdsToSeparatePlainRoleLabels() {
        val first = CharacterNamingPolicy.normalizeNpcCard(
            CharacterCard(id = "legacy_npc_1", name = "店员")
        )
        val second = CharacterNamingPolicy.normalizeNpcCard(
            CharacterCard(id = "legacy_npc_2", name = "店员")
        )
        val secondAgain = CharacterNamingPolicy.normalizeNpcCard(
            CharacterCard(id = "legacy_npc_2", name = "店员")
        )

        assertEquals("阿远", first.name)
        assertEquals("叶青", second.name)
        assertEquals(second.name, secondAgain.name)
    }

    @Test
    fun normalizeNpcCard_keepsConcreteNameAndOccupation() {
        val normalized = CharacterNamingPolicy.normalizeNpcCard(
            CharacterCard(name = "房东陈姐", occupation = "房东兼小店经营者")
        )

        assertEquals("房东陈姐", normalized.name)
        assertEquals("房东兼小店经营者", normalized.occupation)
        assertFalse(CharacterNamingPolicy.isGenericNpcName(normalized.name))
    }

    @Test
    fun normalizeNpcCard_replacesGenericFrontDeskLabel() {
        val normalized = CharacterNamingPolicy.normalizeNpcCard(
            CharacterCard(name = "前台小姑娘", occupation = "公司前台")
        )

        assertEquals("顾宁", normalized.name)
        assertEquals("公司前台", normalized.occupation)
    }

    @Test
    fun normalizeNpcCard_replacesContextualRoleAndRelationshipLabels() {
        val owner = CharacterNamingPolicy.normalizeNpcCard(CharacterCard(name = "彩票店老板"))
        val girlfriend = CharacterNamingPolicy.normalizeNpcCard(CharacterCard(name = "女朋友"))

        assertEquals("老周", owner.name)
        assertEquals("彩票店老板", owner.occupation)
        assertEquals("苏晚", girlfriend.name)
        assertTrue(CharacterNamingPolicy.isGenericNpcName("咖啡店店员"))
        assertTrue(CharacterNamingPolicy.isGenericNpcName("中年男人"))
        assertFalse(CharacterNamingPolicy.isGenericNpcName("店主老周"))
    }

    @Test
    fun normalizeNpcCard_replacesGenericElderlyLabelWithConcreteName() {
        val normalized = CharacterNamingPolicy.normalizeNpcCard(
            CharacterCard(name = "老奶奶", occupation = "早餐摊主")
        )

        assertEquals("陈玉兰", normalized.name)
        assertEquals("早餐摊主", normalized.occupation)
        assertFalse(CharacterNamingPolicy.isGenericNpcName(normalized.name))
    }

    @Test
    fun normalizeNarrativeText_keepsRoleAndAddsConcreteName() {
        val normalized = CharacterNamingPolicy.normalizeNarrativeText(
            "前台小姑娘抬头说话，邻居A从门外经过，店员甲递来收据。"
        )

        assertEquals("前台顾宁抬头说话，邻居王常从门外经过，店员阿远递来收据。", normalized)
    }

    @Test
    fun normalizeNarrativeText_assignsDifferentNamesToDifferentPlaceholders() {
        val normalized = CharacterNamingPolicy.normalizeNarrativeText(
            "邻居A向他点头，邻居B从电梯里走出来。"
        )

        assertEquals("邻居王常向他点头，邻居李雯从电梯里走出来。", normalized)
    }

    @Test
    fun normalizeNarrativeText_doesNotRewriteOrdinaryChineseGrammar() {
        val text = "邻居一边走一边说，店员一看就笑了，前台接待来宾。"

        assertEquals(text, CharacterNamingPolicy.normalizeNarrativeText(text))
    }

    @Test
    fun isGenericNpcName_filtersWuxiaBystandersWithoutRejectingNamedCharacters() {
        assertTrue(CharacterNamingPolicy.isGenericNpcName("卖茶老汉"))
        assertTrue(CharacterNamingPolicy.isGenericNpcName("赶车汉子"))
        assertTrue(CharacterNamingPolicy.isGenericNpcName("胖商人"))
        assertTrue(CharacterNamingPolicy.isGenericNpcName("精瘦江湖客"))
        assertTrue(CharacterNamingPolicy.isGenericNpcName("店小二"))
        assertFalse(CharacterNamingPolicy.isGenericNpcName("小龙女"))
        assertFalse(CharacterNamingPolicy.isGenericNpcName("金轮法王"))
        assertFalse(CharacterNamingPolicy.isGenericNpcName("孙婆婆"))
    }
}
