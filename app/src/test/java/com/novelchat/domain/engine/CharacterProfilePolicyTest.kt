package com.novelchat.domain.engine

import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.Relationship
import org.junit.Assert.assertEquals
import org.junit.Test

class CharacterProfilePolicyTest {

    @Test
    fun reconcile_replacesAmbiguousOccupationAndSingleDefaultForConfirmedGirlfriend() {
        val card = CharacterCard(
            name = "苏晚",
            occupation = "学生或职员",
            gender = "",
            relationshipStatus = "单身",
            relationships = mapOf("林克" to Relationship(stage = "女朋友"))
        )

        val reconciled = CharacterProfilePolicy.reconcile(card, "林克")

        assertEquals("林克的女朋友", reconciled.occupation)
        assertEquals("女", reconciled.gender)
        assertEquals("恋爱中", reconciled.relationshipStatus)
    }

    @Test
    fun reconcile_preservesConcreteStableProfile() {
        val card = CharacterCard(
            name = "老周",
            occupation = "彩票店店主",
            gender = "男",
            relationshipStatus = "已婚",
            relationships = mapOf("林克" to Relationship(stage = "熟悉的店主"))
        )

        assertEquals(card, CharacterProfilePolicy.reconcile(card, "林克"))
    }

    @Test
    fun reconcile_replacesLegacyPlaceholderNameWithConcreteIdentity() {
        val reconciled = CharacterProfilePolicy.reconcile(
            CharacterCard(id = "邻居A", name = "邻居A", occupation = "邻居"),
            "林克"
        )

        assertEquals("王常", reconciled.id)
        assertEquals("王常", reconciled.name)
        assertEquals("邻居", reconciled.occupation)
    }
}
