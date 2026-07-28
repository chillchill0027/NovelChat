package com.novelchat.domain.engine

import com.novelchat.data.model.CharacterStatePatch
import com.novelchat.data.model.InventoryChange
import com.novelchat.data.model.MentalStatePatch
import com.novelchat.data.model.PhysicalStatePatch
import com.novelchat.data.model.RelationshipPatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterStatePatchContentPolicyTest {

    @Test
    fun normalize_boundsTextCollectionsAndDeltas() {
        val normalized = CharacterStatePatchContentPolicy.normalize(
            CharacterStatePatch(
                emotion = "惊".repeat(500),
                physicalState = PhysicalStatePatch(
                    injuries = List(30) { "伤".repeat(1_500) }
                ),
                mentalState = MentalStatePatch(currentThoughts = "想".repeat(3_000)),
                relationshipToPlayer = RelationshipPatch(
                    favorDelta = 500,
                    trustDelta = -500,
                    stage = "关系".repeat(100)
                ),
                inventoryChanges = List(70) { index ->
                    InventoryChange(action = "add", item = "物品$index", quantity = 99_999)
                }
            )
        )

        assertEquals(120, normalized.emotion?.length)
        assertEquals(20, normalized.physicalState?.injuries?.size)
        assertEquals(1_000, normalized.physicalState?.injuries?.first()?.length)
        assertEquals(2_000, normalized.mentalState?.currentThoughts?.length)
        assertEquals(100, normalized.relationshipToPlayer?.favorDelta)
        assertEquals(-100, normalized.relationshipToPlayer?.trustDelta)
        assertEquals(50, normalized.inventoryChanges.size)
        assertEquals(9_999, normalized.inventoryChanges.first().quantity)
    }

    @Test
    fun normalize_dropsInvalidInventoryChanges() {
        val normalized = CharacterStatePatchContentPolicy.normalize(
            CharacterStatePatch(
                inventoryChanges = listOf(
                    InventoryChange(action = "inspect", item = "钥匙"),
                    InventoryChange(action = "add", item = " ")
                )
            )
        )

        assertTrue(normalized.inventoryChanges.isEmpty())
    }
}
