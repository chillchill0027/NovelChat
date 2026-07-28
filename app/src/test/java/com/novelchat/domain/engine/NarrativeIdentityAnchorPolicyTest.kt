package com.novelchat.domain.engine

import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.NarrativeResult
import com.novelchat.data.model.Relationship
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NarrativeIdentityAnchorPolicyTest {

    @Test
    fun resolveAndApply_assignsStableRealNameToNewGirlfriend() {
        val anchors = NarrativeIdentityAnchorPolicy.resolve(
            userMessage = "安置好女朋友之后去楼下买咖啡",
            knownCharacters = emptyList(),
            playerName = "林克"
        )

        assertEquals(1, anchors.size)
        assertEquals("苏晚", anchors.single().name)
        assertFalse(anchors.single().isExistingCharacter)

        val result = NarrativeIdentityAnchorPolicy.apply(
            result = NarrativeResult(
                narrative = "林克确认女友安顿妥帖后，才起身离开出租屋。"
            ),
            anchors = anchors,
            playerName = "林克"
        )

        assertTrue(result.narrative.contains("女友苏晚"))
        assertEquals("苏晚", result.introducedCharacters.single().name)
        assertEquals("女友", result.introducedCharacters.single().relationshipToPlayer)
    }

    @Test
    fun resolveAndApply_reusesExistingPartnerWithoutDuplicateIntroduction() {
        val anchors = NarrativeIdentityAnchorPolicy.resolve(
            userMessage = "照顾好我的girlfriend以后出门",
            knownCharacters = listOf(
                CharacterCard(
                    name = "顾宁",
                    occupation = "建筑设计师",
                    relationships = mapOf(
                        "林克" to Relationship(stage = "女朋友")
                    )
                )
            ),
            playerName = "林克"
        )

        assertEquals("顾宁", anchors.single().name)
        assertTrue(anchors.single().isExistingCharacter)

        val result = NarrativeIdentityAnchorPolicy.apply(
            result = NarrativeResult(
                narrative = "林克替女友放好温水，确认她能休息后离开。",
                introducedCharacters = emptyList()
            ),
            anchors = anchors,
            playerName = "林克"
        )

        assertTrue(result.narrative.contains("女友顾宁"))
        assertTrue(result.introducedCharacters.isEmpty())
    }
}
