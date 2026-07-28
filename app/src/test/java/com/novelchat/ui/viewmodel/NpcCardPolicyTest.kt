package com.novelchat.ui.viewmodel

import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.Relationship
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NpcCardPolicyTest {

    @Test
    fun exactRelationshipKeepsAuthoritativeZeroFavor() {
        val snapshot = NpcCardPolicy.relationshipSnapshot(
            card = CharacterCard(
                name = "苏晚",
                favor = 55,
                relationships = mapOf("林克" to Relationship(favor = 0, trust = 42, stage = "恋人"))
            ),
            playerName = "林克"
        )

        assertEquals(0, snapshot.favor)
        assertEquals(42, snapshot.trust)
        assertEquals("恋人", snapshot.stage)
    }

    @Test
    fun uniqueQualifiedPlayerKeyCanBeResolved() {
        val snapshot = NpcCardPolicy.relationshipSnapshot(
            card = CharacterCard(
                favor = 5,
                relationships = mapOf("林克（主角）" to Relationship(favor = 30, trust = 68))
            ),
            playerName = "林克"
        )

        assertEquals(30, snapshot.favor)
        assertEquals(68, snapshot.trust)
        assertEquals("友好", snapshot.stage)
    }

    @Test
    fun ambiguousOrBlankKeysFallBackWithoutInventingTrust() {
        val card = CharacterCard(
            favor = -20,
            relationships = mapOf(
                "" to Relationship(favor = 80, trust = 90),
                "林克本人" to Relationship(favor = 50, trust = 60),
                "主角林克" to Relationship(favor = 45, trust = 55)
            )
        )

        val snapshot = NpcCardPolicy.relationshipSnapshot(card, "林克")

        assertEquals(-20, snapshot.favor)
        assertNull(snapshot.trust)
        assertEquals("冷淡", snapshot.stage)
    }

    @Test
    fun blankPlayerNameDoesNotMatchArbitraryRelationship() {
        val snapshot = NpcCardPolicy.relationshipSnapshot(
            CharacterCard(
                favor = 12,
                relationships = mapOf("其他人" to Relationship(favor = 90, trust = 90))
            ),
            ""
        )

        assertEquals(12, snapshot.favor)
        assertNull(snapshot.trust)
        assertEquals("陌生", snapshot.stage)
    }

    @Test
    fun displayStage_normalizesStackedLabelsAndTrustShowsItsScale() {
        val snapshot = NpcCardPolicy.relationshipSnapshot(
            CharacterCard(
                relationships = mapOf(
                    "林克" to Relationship(
                        favor = 3,
                        trust = 120,
                        stage = "服务者 / 引导者"
                    )
                )
            ),
            "林克"
        )

        assertEquals("服务者 · 引导者", snapshot.stage)
        assertEquals(100, snapshot.trust)
        assertEquals("100/100", NpcCardPolicy.trustLabel(snapshot.trust))
        assertEquals("未知", NpcCardPolicy.trustLabel(null))
    }
}
