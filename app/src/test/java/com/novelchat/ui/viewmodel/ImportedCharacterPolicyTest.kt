package com.novelchat.ui.viewmodel

import com.novelchat.data.model.CharacterLore
import com.novelchat.data.model.InventoryItem
import com.novelchat.data.model.MentalState
import com.novelchat.data.model.PhysicalState
import com.novelchat.data.remote.model.ParsedNpc
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportedCharacterPolicyTest {

    @Test
    fun createCard_preservesNamedRelationshipToPlayer() {
        val card = ImportedCharacterPolicy.createCard(
            npc = ParsedNpc(
                name = "苏晚",
                gender = "女",
                occupation = "插画师",
                relationshipToPlayer = "女朋友"
            ),
            playerName = "林克",
            createdAt = "2026-07-18T10:00:00"
        )

        assertEquals("恋爱中", card.relationshipStatus)
        assertEquals("女朋友", card.relationships.getValue("林克").stage)
    }

    @Test
    fun createCard_replacesGenericIdentityWithConcreteName() {
        val card = ImportedCharacterPolicy.createCard(
            npc = ParsedNpc(name = "邻居A", occupation = "邻居"),
            playerName = "林克",
            createdAt = "2026-07-18T10:00:00"
        )

        assertEquals("王常", card.name)
        assertEquals("王常", card.id)
    }

    @Test
    fun createCard_preservesLongTermLoreAndInitialState() {
        val card = ImportedCharacterPolicy.createCard(
            npc = ParsedNpc(
                name = "顾宁",
                occupation = "建筑设计师",
                lore = CharacterLore(
                    goals = listOf("完成文化中心项目"),
                    speechStyle = "直接、具体"
                ),
                physicalState = PhysicalState(stamina = 72, fatigue = "中等"),
                mentalState = MentalState(mood = "克制", focus = "项目交付"),
                inventory = listOf(InventoryItem(name = "自动铅笔"))
            ),
            playerName = "林克",
            createdAt = "2026-07-26T10:00:00"
        )

        assertEquals(listOf("完成文化中心项目"), card.lore.goals)
        assertEquals("直接、具体", card.lore.speechStyle)
        assertEquals(72, card.physicalState.stamina)
        assertEquals("克制", card.emotion)
        assertEquals("自动铅笔", card.inventory.single().name)
    }
}
