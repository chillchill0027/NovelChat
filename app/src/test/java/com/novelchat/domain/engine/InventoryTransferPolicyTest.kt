package com.novelchat.domain.engine

import com.novelchat.data.model.InventoryItem
import com.novelchat.data.model.InventoryTransferJournal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryTransferPolicyTest {

    @Test
    fun prepare_movesOnlyAvailableQuantityAndPreservesItemMetadata() {
        val source = listOf(
            InventoryItem(
                name = "中奖彩票",
                quantity = 3,
                type = "凭证",
                description = "一等奖票据",
                origin = "云桥里彩票店"
            ),
            InventoryItem(name = "身份证", quantity = 1)
        )
        val target = listOf(InventoryItem(name = "登记册", quantity = 1))

        val decision = InventoryTransferPolicy.prepare(
            round = 7,
            itemName = "中奖彩票",
            quantity = 2,
            sourceKey = "player",
            targetKey = "npc_zhouming",
            sourceInventory = source,
            targetInventory = target
        )

        val plan = requireNotNull(decision.plan)
        assertNull(decision.failure)
        assertEquals(1, InventoryTransferPolicy.totalQuantity(plan.sourceInventory, "中奖彩票"))
        assertEquals(2, InventoryTransferPolicy.totalQuantity(plan.targetInventory, "中奖彩票"))
        val transferred = plan.targetInventory.single { it.name == "中奖彩票" }
        assertEquals("凭证", transferred.type)
        assertEquals("一等奖票据", transferred.description)
        assertEquals("云桥里彩票店", transferred.origin)
        assertEquals(3, plan.journal.sourceBeforeQuantity)
        assertEquals(0, plan.journal.targetBeforeQuantity)
    }

    @Test
    fun prepare_rejectsMissingOrInsufficientSourceWithoutCreatingTargetItem() {
        val missing = InventoryTransferPolicy.prepare(
            round = 7,
            itemName = "中奖彩票",
            quantity = 1,
            sourceKey = "player",
            targetKey = "npc_zhouming",
            sourceInventory = emptyList(),
            targetInventory = emptyList()
        )
        val insufficient = InventoryTransferPolicy.prepare(
            round = 7,
            itemName = "中奖彩票",
            quantity = 2,
            sourceKey = "player",
            targetKey = "npc_zhouming",
            sourceInventory = listOf(InventoryItem(name = "中奖彩票", quantity = 1)),
            targetInventory = emptyList()
        )

        assertEquals(InventoryTransferPolicy.Failure.SOURCE_MISSING, missing.failure)
        assertNull(missing.plan)
        assertEquals(InventoryTransferPolicy.Failure.SOURCE_INSUFFICIENT, insufficient.failure)
        assertNull(insufficient.plan)
    }

    @Test
    fun prepare_rejectsTargetQuantityOverflow() {
        val decision = InventoryTransferPolicy.prepare(
            round = 7,
            itemName = "硬币",
            quantity = 2,
            sourceKey = "player",
            targetKey = "npc_zhouming",
            sourceInventory = listOf(InventoryItem(name = "硬币", quantity = 10)),
            targetInventory = listOf(InventoryItem(name = "硬币", quantity = 9_998))
        )

        assertEquals(InventoryTransferPolicy.Failure.TARGET_OVERFLOW, decision.failure)
        assertNull(decision.plan)
    }

    @Test
    fun resume_afterSourceWriteOnlyCompletesTargetWithoutChargingSourceAgain() {
        val prepared = requireNotNull(
            InventoryTransferPolicy.prepare(
                round = 7,
                itemName = "中奖彩票",
                quantity = 1,
                sourceKey = "player",
                targetKey = "npc_zhouming",
                sourceInventory = listOf(InventoryItem(name = "中奖彩票", quantity = 2)),
                targetInventory = emptyList()
            ).plan
        )

        val resumed = InventoryTransferPolicy.resume(
            journal = prepared.journal,
            requestedItemName = "中奖彩票",
            requestedQuantity = 1,
            sourceKey = "player",
            targetKey = "npc_zhouming",
            sourceInventory = prepared.sourceInventory,
            targetInventory = emptyList()
        )

        val plan = requireNotNull(resumed.plan)
        assertFalse(plan.writeSource)
        assertTrue(plan.writeTarget)
        assertEquals(1, InventoryTransferPolicy.totalQuantity(plan.sourceInventory, "中奖彩票"))
        assertEquals(1, InventoryTransferPolicy.totalQuantity(plan.targetInventory, "中奖彩票"))
    }

    @Test
    fun resume_afterBothWritesTreatsPreparedJournalAsAlreadyCompleted() {
        val prepared = requireNotNull(
            InventoryTransferPolicy.prepare(
                round = 7,
                itemName = "中奖彩票",
                quantity = 1,
                sourceKey = "player",
                targetKey = "npc_zhouming",
                sourceInventory = listOf(InventoryItem(name = "中奖彩票", quantity = 1)),
                targetInventory = emptyList()
            ).plan
        )

        val resumed = InventoryTransferPolicy.resume(
            journal = prepared.journal,
            requestedItemName = "中奖彩票",
            requestedQuantity = 1,
            sourceKey = "player",
            targetKey = "npc_zhouming",
            sourceInventory = prepared.sourceInventory,
            targetInventory = prepared.targetInventory
        )

        val plan = requireNotNull(resumed.plan)
        assertTrue(plan.alreadyCompleted)
        assertFalse(plan.writeSource)
        assertFalse(plan.writeTarget)
        assertEquals(InventoryTransferJournal.STATUS_COMPLETED, plan.journal.status)
    }

    @Test
    fun resume_rejectsChangedQuantityOrUnrecognizedInventoryState() {
        val prepared = requireNotNull(
            InventoryTransferPolicy.prepare(
                round = 7,
                itemName = "中奖彩票",
                quantity = 1,
                sourceKey = "player",
                targetKey = "npc_zhouming",
                sourceInventory = listOf(InventoryItem(name = "中奖彩票", quantity = 3)),
                targetInventory = emptyList()
            ).plan
        )
        val changedQuantity = InventoryTransferPolicy.resume(
            journal = prepared.journal,
            requestedItemName = "中奖彩票",
            requestedQuantity = 2,
            sourceKey = "player",
            targetKey = "npc_zhouming",
            sourceInventory = listOf(InventoryItem(name = "中奖彩票", quantity = 3)),
            targetInventory = emptyList()
        )
        val driftedState = InventoryTransferPolicy.resume(
            journal = prepared.journal,
            requestedItemName = "中奖彩票",
            requestedQuantity = 1,
            sourceKey = "player",
            targetKey = "npc_zhouming",
            sourceInventory = listOf(InventoryItem(name = "中奖彩票", quantity = 99)),
            targetInventory = emptyList()
        )

        assertEquals(InventoryTransferPolicy.Failure.JOURNAL_CONFLICT, changedQuantity.failure)
        assertEquals(InventoryTransferPolicy.Failure.JOURNAL_CONFLICT, driftedState.failure)
    }

    @Test
    fun resume_completedJournalIgnoresLaterLegitimateInventoryChanges() {
        val prepared = requireNotNull(
            InventoryTransferPolicy.prepare(
                round = 7,
                itemName = "硬币",
                quantity = 1,
                sourceKey = "player",
                targetKey = "npc_zhouming",
                sourceInventory = listOf(InventoryItem(name = "硬币", quantity = 5)),
                targetInventory = emptyList()
            ).plan
        )
        val completed = InventoryTransferPolicy.markCompleted(prepared.journal)

        val resumed = InventoryTransferPolicy.resume(
            journal = completed,
            requestedItemName = "硬币",
            requestedQuantity = 1,
            sourceKey = "player",
            targetKey = "npc_zhouming",
            sourceInventory = listOf(InventoryItem(name = "硬币", quantity = 2)),
            targetInventory = listOf(InventoryItem(name = "硬币", quantity = 3))
        )

        val plan = requireNotNull(resumed.plan)
        assertTrue(plan.alreadyCompleted)
        assertFalse(plan.writeSource)
        assertFalse(plan.writeTarget)
    }

    @Test
    fun prepare_consolidatesDuplicateInventoryRowsDeterministically() {
        val decision = InventoryTransferPolicy.prepare(
            round = 7,
            itemName = "硬币",
            quantity = 2,
            sourceKey = "player",
            targetKey = "npc_zhouming",
            sourceInventory = listOf(
                InventoryItem(name = "硬币", quantity = 1),
                InventoryItem(name = " 硬币 ", quantity = 3),
                InventoryItem(name = "钥匙", quantity = 1)
            ),
            targetInventory = listOf(
                InventoryItem(name = "硬币", quantity = 2),
                InventoryItem(name = "硬币", quantity = 1)
            )
        )

        val plan = requireNotNull(decision.plan)
        assertEquals(1, plan.sourceInventory.count { it.name.trim() == "硬币" })
        assertEquals(2, InventoryTransferPolicy.totalQuantity(plan.sourceInventory, "硬币"))
        assertEquals(1, plan.targetInventory.count { it.name.trim() == "硬币" })
        assertEquals(5, InventoryTransferPolicy.totalQuantity(plan.targetInventory, "硬币"))
    }
}
