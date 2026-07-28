package com.novelchat.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CharacterReferencePolicyTest {

    @Test
    fun resolve_prefersExactNameOrStableId() {
        val card = CharacterCard(id = "npc_42", name = "叶青", occupation = "咖啡店店员")

        assertEquals(card, CharacterReferencePolicy.resolve(listOf(card), "叶青"))
        assertEquals(card, CharacterReferencePolicy.resolve(listOf(card), "npc_42"))
    }

    @Test
    fun resolve_usesUniqueRoleWhenLegacySceneStillHasGenericName() {
        val card = CharacterCard(id = "legacy_npc_2", name = "叶青", occupation = "咖啡店店员")

        assertEquals(card, CharacterReferencePolicy.resolve(listOf(card), "店员"))
    }

    @Test
    fun resolve_doesNotGuessBetweenMultipleCharactersWithSameRole() {
        val cards = listOf(
            CharacterCard(name = "阿远", occupation = "咖啡店店员"),
            CharacterCard(name = "叶青", occupation = "咖啡店店员")
        )

        assertNull(CharacterReferencePolicy.resolve(cards, "店员"))
    }

    @Test
    fun resolve_matchesConfirmedRelationshipRole() {
        val card = CharacterCard(
            name = "顾遥",
            occupation = "插画师",
            relationships = mapOf("林克" to Relationship(stage = "女朋友"))
        )

        assertEquals(card, CharacterReferencePolicy.resolve(listOf(card), "女朋友"))
    }
}
