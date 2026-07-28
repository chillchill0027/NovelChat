package com.novelchat.domain.engine

import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.CharacterIdentityPolicy
import com.novelchat.data.model.MentalState
import com.novelchat.data.model.Relationship
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterRosterIdentityPolicyTest {

    @Test
    fun reconcile_keepsRicherNamedCardActiveAndPoolsSparseAlias() {
        val sparseAlias = CharacterCard(
            id = "陈玉兰",
            name = "陈玉兰",
            occupation = "早餐摊贩",
            appearance = "年迈，正在摆放早餐",
            status = "active",
            lastModified = "2026-07-22T13:05:37"
        )
        val confirmedCard = CharacterCard(
            id = "王奶奶",
            name = "王奶奶",
            occupation = "早餐摊店主",
            appearance = "老奶奶，正在忙碌中",
            status = "active",
            mentalState = MentalState(
                mood = "关切",
                focus = "林克要办的事情",
                currentThoughts = "看起来是件大事"
            ),
            relationships = mapOf(
                "林克" to Relationship(favor = 5, trust = 3, stage = "关心者")
            ),
            lastModified = "2026-07-22T13:17:42"
        )

        val result = CharacterIdentityPolicy.reconcileRoster(
            listOf(sparseAlias, confirmedCard)
        )

        assertEquals("pool", result.cards.single { it.name == "陈玉兰" }.status)
        assertEquals("active", result.cards.single { it.name == "王奶奶" }.status)
        assertEquals("王奶奶", result.aliases["陈玉兰"])
    }

    @Test
    fun reconcile_doesNotMergeUnrelatedOrFullyDevelopedCharacters() {
        val first = CharacterCard(
            name = "王奶奶",
            occupation = "早餐摊店主",
            appearance = "老奶奶",
            mentalState = MentalState(mood = "平静", focus = "早餐摊")
        )
        val second = CharacterCard(
            name = "陈奶奶",
            occupation = "早餐摊帮工",
            appearance = "老奶奶",
            mentalState = MentalState(mood = "忙碌", focus = "招呼顾客")
        )

        val result = CharacterIdentityPolicy.reconcileRoster(listOf(first, second))

        assertTrue(result.aliases.isEmpty())
        assertEquals(listOf("active", "active"), result.cards.map { it.status })
    }
}
