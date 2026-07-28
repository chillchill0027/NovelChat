package com.novelchat.domain.engine

import com.novelchat.data.model.InventoryItem
import com.novelchat.data.model.InventoryTransferJournal
import java.nio.charset.StandardCharsets
import java.util.UUID

internal object InventoryTransferPolicy {
    private const val MAX_ITEM_QUANTITY = 9_999

    enum class Failure {
        INVALID_TRANSFER,
        SOURCE_MISSING,
        SOURCE_INSUFFICIENT,
        TARGET_OVERFLOW,
        JOURNAL_CONFLICT
    }

    data class Plan(
        val journal: InventoryTransferJournal,
        val sourceInventory: List<InventoryItem>,
        val targetInventory: List<InventoryItem>,
        val writeSource: Boolean,
        val writeTarget: Boolean,
        val alreadyCompleted: Boolean = false
    )

    data class Decision(
        val plan: Plan? = null,
        val failure: Failure? = null
    )

    fun operationId(
        round: Int,
        itemName: String,
        sourceKey: String,
        targetKey: String
    ): String {
        val identity = listOf(
            round.toString(),
            canonical(sourceKey),
            canonical(targetKey),
            canonical(itemName)
        ).joinToString("|")
        return "inventory_" + UUID.nameUUIDFromBytes(
            identity.toByteArray(StandardCharsets.UTF_8)
        )
    }

    fun prepare(
        round: Int,
        itemName: String,
        quantity: Int,
        sourceKey: String,
        targetKey: String,
        sourceInventory: List<InventoryItem>,
        targetInventory: List<InventoryItem>
    ): Decision {
        val normalizedItem = itemName.trim()
        if (
            round <= 0 ||
            normalizedItem.isBlank() ||
            quantity !in 1..MAX_ITEM_QUANTITY ||
            sourceKey.isBlank() ||
            targetKey.isBlank() ||
            canonical(sourceKey) == canonical(targetKey)
        ) {
            return Decision(failure = Failure.INVALID_TRANSFER)
        }

        val sourceQuantity = totalQuantity(sourceInventory, normalizedItem)
        if (sourceQuantity <= 0) return Decision(failure = Failure.SOURCE_MISSING)
        if (sourceQuantity < quantity) return Decision(failure = Failure.SOURCE_INSUFFICIENT)

        val targetQuantity = totalQuantity(targetInventory, normalizedItem)
        if (targetQuantity > MAX_ITEM_QUANTITY - quantity) {
            return Decision(failure = Failure.TARGET_OVERFLOW)
        }

        val itemTemplate = sourceInventory.first { matches(it.name, normalizedItem) }
            .copy(name = normalizedItem, quantity = quantity)
        val journal = InventoryTransferJournal(
            operationId = operationId(round, normalizedItem, sourceKey, targetKey),
            round = round,
            item = itemTemplate,
            quantity = quantity,
            sourceKey = sourceKey,
            targetKey = targetKey,
            sourceBeforeQuantity = sourceQuantity,
            targetBeforeQuantity = targetQuantity
        )
        return Decision(
            plan = planForExpectedQuantities(
                journal = journal,
                sourceInventory = sourceInventory,
                targetInventory = targetInventory,
                writeSource = true,
                writeTarget = true
            )
        )
    }

    fun resume(
        journal: InventoryTransferJournal,
        requestedItemName: String,
        requestedQuantity: Int,
        sourceKey: String,
        targetKey: String,
        sourceInventory: List<InventoryItem>,
        targetInventory: List<InventoryItem>
    ): Decision {
        val expectedOperationId = operationId(
            round = journal.round,
            itemName = requestedItemName,
            sourceKey = sourceKey,
            targetKey = targetKey
        )
        if (
            journal.operationId != expectedOperationId ||
            journal.quantity != requestedQuantity ||
            !matches(journal.item.name, requestedItemName) ||
            canonical(journal.sourceKey) != canonical(sourceKey) ||
            canonical(journal.targetKey) != canonical(targetKey) ||
            journal.status !in setOf(
                InventoryTransferJournal.STATUS_PREPARED,
                InventoryTransferJournal.STATUS_COMPLETED
            )
        ) {
            return Decision(failure = Failure.JOURNAL_CONFLICT)
        }

        val sourceCurrent = totalQuantity(sourceInventory, journal.item.name)
        val targetCurrent = totalQuantity(targetInventory, journal.item.name)
        val sourceAfter = journal.sourceBeforeQuantity - journal.quantity
        val targetAfter = journal.targetBeforeQuantity + journal.quantity
        if (journal.status == InventoryTransferJournal.STATUS_COMPLETED) {
            return Decision(
                plan = Plan(
                    journal = journal,
                    sourceInventory = sourceInventory,
                    targetInventory = targetInventory,
                    writeSource = false,
                    writeTarget = false,
                    alreadyCompleted = true
                )
            )
        }
        val sourceIsBefore = sourceCurrent == journal.sourceBeforeQuantity
        val sourceIsAfter = sourceCurrent == sourceAfter
        val targetIsBefore = targetCurrent == journal.targetBeforeQuantity
        val targetIsAfter = targetCurrent == targetAfter

        if ((!sourceIsBefore && !sourceIsAfter) || (!targetIsBefore && !targetIsAfter)) {
            return Decision(failure = Failure.JOURNAL_CONFLICT)
        }
        if (sourceIsAfter && targetIsAfter) {
            return Decision(
                plan = Plan(
                    journal = journal.copy(status = InventoryTransferJournal.STATUS_COMPLETED),
                    sourceInventory = sourceInventory,
                    targetInventory = targetInventory,
                    writeSource = false,
                    writeTarget = false,
                    alreadyCompleted = true
                )
            )
        }

        return Decision(
            plan = planForExpectedQuantities(
                journal = journal.copy(status = InventoryTransferJournal.STATUS_PREPARED),
                sourceInventory = sourceInventory,
                targetInventory = targetInventory,
                writeSource = sourceIsBefore,
                writeTarget = targetIsBefore
            )
        )
    }

    fun markCompleted(journal: InventoryTransferJournal): InventoryTransferJournal {
        return journal.copy(status = InventoryTransferJournal.STATUS_COMPLETED)
    }

    fun totalQuantity(inventory: List<InventoryItem>, itemName: String): Int {
        return inventory
            .asSequence()
            .filter { matches(it.name, itemName) }
            .fold(0L) { total, item -> total + item.quantity.coerceAtLeast(0) }
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }

    private fun planForExpectedQuantities(
        journal: InventoryTransferJournal,
        sourceInventory: List<InventoryItem>,
        targetInventory: List<InventoryItem>,
        writeSource: Boolean,
        writeTarget: Boolean
    ): Plan {
        val sourceAfter = journal.sourceBeforeQuantity - journal.quantity
        val targetAfter = journal.targetBeforeQuantity + journal.quantity
        return Plan(
            journal = journal,
            sourceInventory = if (writeSource) {
                withTotalQuantity(
                    inventory = sourceInventory,
                    template = journal.item,
                    total = sourceAfter
                )
            } else {
                sourceInventory
            },
            targetInventory = if (writeTarget) {
                withTotalQuantity(
                    inventory = targetInventory,
                    template = journal.item,
                    total = targetAfter
                )
            } else {
                targetInventory
            },
            writeSource = writeSource,
            writeTarget = writeTarget
        )
    }

    private fun withTotalQuantity(
        inventory: List<InventoryItem>,
        template: InventoryItem,
        total: Int
    ): List<InventoryItem> {
        val firstMatch = inventory.indexOfFirst { matches(it.name, template.name) }
        if (firstMatch < 0) {
            return if (total > 0) {
                inventory + template.copy(quantity = total)
            } else {
                inventory
            }
        }

        val normalized = ArrayList<InventoryItem>(inventory.size)
        inventory.forEachIndexed { index, item ->
            if (!matches(item.name, template.name)) {
                normalized += item
            } else if (index == firstMatch && total > 0) {
                normalized += item.copy(quantity = total)
            }
        }
        return normalized
    }

    private fun matches(left: String, right: String): Boolean {
        return canonical(left) == canonical(right)
    }

    private fun canonical(value: String): String = value.trim().lowercase()
}
