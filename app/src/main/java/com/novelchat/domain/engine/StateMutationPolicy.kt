package com.novelchat.domain.engine

import com.google.gson.JsonElement
import com.novelchat.data.model.StateMutationJournal
import java.nio.charset.StandardCharsets
import java.util.UUID

internal object StateMutationPolicy {

    enum class Failure {
        INVALID_MUTATION,
        JOURNAL_CONFLICT
    }

    data class Snapshot(
        val exists: Boolean,
        val state: JsonElement? = null
    )

    data class Plan(
        val journal: StateMutationJournal,
        val applyAfter: Boolean,
        val alreadyCompleted: Boolean = false
    )

    data class Decision(
        val plan: Plan? = null,
        val failure: Failure? = null
    )

    fun operationId(round: Int, operationKey: String): String {
        val identity = "$round|${operationKey.trim().lowercase()}"
        return "state_" + UUID.nameUUIDFromBytes(
            identity.toByteArray(StandardCharsets.UTF_8)
        )
    }

    fun prepare(
        round: Int,
        operationKey: String,
        entityType: String,
        targetKey: String,
        before: Snapshot,
        after: Snapshot
    ): Decision {
        if (
            round <= 0 ||
            operationKey.isBlank() ||
            entityType !in supportedEntityTypes ||
            targetKey.isBlank() ||
            !snapshotIsValid(before) ||
            !snapshotIsValid(after) ||
            before == after
        ) {
            return Decision(failure = Failure.INVALID_MUTATION)
        }
        val journal = StateMutationJournal(
            operationId = operationId(round, operationKey),
            operationKey = operationKey.trim(),
            round = round,
            entityType = entityType,
            targetKey = targetKey,
            beforeExists = before.exists,
            beforeState = before.state,
            afterExists = after.exists,
            afterState = after.state
        )
        return Decision(plan = Plan(journal = journal, applyAfter = true))
    }

    fun resume(
        journal: StateMutationJournal,
        expectedOperationId: String,
        expectedEntityType: String,
        expectedTargetKey: String,
        current: Snapshot
    ): Decision {
        if (
            journal.operationId != expectedOperationId ||
            journal.operationId != operationId(journal.round, journal.operationKey) ||
            journal.operationKey.isBlank() ||
            journal.entityType != expectedEntityType ||
            journal.targetKey != expectedTargetKey ||
            journal.entityType !in supportedEntityTypes ||
            journal.status !in setOf(
                StateMutationJournal.STATUS_PREPARED,
                StateMutationJournal.STATUS_COMPLETED
            ) ||
            !snapshotIsValid(current) ||
            !snapshotIsValid(journal.toBeforeSnapshot()) ||
            !snapshotIsValid(journal.toAfterSnapshot())
        ) {
            return Decision(failure = Failure.JOURNAL_CONFLICT)
        }
        if (journal.status == StateMutationJournal.STATUS_COMPLETED) {
            return Decision(
                plan = Plan(
                    journal = journal,
                    applyAfter = false,
                    alreadyCompleted = true
                )
            )
        }

        val before = journal.toBeforeSnapshot()
        val after = journal.toAfterSnapshot()
        return when (current) {
            after -> Decision(
                plan = Plan(
                    journal = markCompleted(journal),
                    applyAfter = false,
                    alreadyCompleted = true
                )
            )
            before -> Decision(plan = Plan(journal = journal, applyAfter = true))
            else -> Decision(failure = Failure.JOURNAL_CONFLICT)
        }
    }

    fun markCompleted(journal: StateMutationJournal): StateMutationJournal {
        return journal.copy(status = StateMutationJournal.STATUS_COMPLETED)
    }

    fun beforeSnapshot(journal: StateMutationJournal): Snapshot = journal.toBeforeSnapshot()

    fun afterSnapshot(journal: StateMutationJournal): Snapshot = journal.toAfterSnapshot()

    private fun snapshotIsValid(snapshot: Snapshot): Boolean {
        return if (snapshot.exists) {
            snapshot.state != null && !snapshot.state.isJsonNull
        } else {
            snapshot.state == null || snapshot.state.isJsonNull
        }
    }

    private fun StateMutationJournal.toBeforeSnapshot(): Snapshot {
        return Snapshot(beforeExists, beforeState)
    }

    private fun StateMutationJournal.toAfterSnapshot(): Snapshot {
        return Snapshot(afterExists, afterState)
    }

    private val supportedEntityTypes = setOf(
        StateMutationJournal.ENTITY_PLAYER,
        StateMutationJournal.ENTITY_CHARACTER,
        StateMutationJournal.ENTITY_WORLDBOOK
    )
}
