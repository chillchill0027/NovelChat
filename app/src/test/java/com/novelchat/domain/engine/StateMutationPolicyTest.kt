package com.novelchat.domain.engine

import com.google.gson.JsonParser
import com.novelchat.data.model.StateMutationJournal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StateMutationPolicyTest {

    @Test
    fun prepare_createsStablePreparedJournalForChangedEntity() {
        val before = snapshot("""{"favor":1,"trust":2}""")
        val after = snapshot("""{"favor":4,"trust":3}""")

        val decision = StateMutationPolicy.prepare(
            round = 8,
            operationKey = "character_state:npc_zhouming",
            entityType = StateMutationJournal.ENTITY_CHARACTER,
            targetKey = "npc_zhouming",
            before = before,
            after = after
        )

        val plan = requireNotNull(decision.plan)
        assertNull(decision.failure)
        assertTrue(plan.applyAfter)
        assertFalse(plan.alreadyCompleted)
        assertEquals(StateMutationJournal.STATUS_PREPARED, plan.journal.status)
        assertEquals(
            StateMutationPolicy.operationId(8, "character_state:npc_zhouming"),
            plan.journal.operationId
        )
    }

    @Test
    fun prepare_rejectsNoOpMutation() {
        val same = snapshot("""{"inventory":[]}""")

        val decision = StateMutationPolicy.prepare(
            round = 8,
            operationKey = "player_update",
            entityType = StateMutationJournal.ENTITY_PLAYER,
            targetKey = "player",
            before = same,
            after = same
        )

        assertEquals(StateMutationPolicy.Failure.INVALID_MUTATION, decision.failure)
        assertNull(decision.plan)
    }

    @Test
    fun resume_appliesAfterSnapshotWhenEntityStillMatchesBefore() {
        val before = snapshot("""{"favor":1}""")
        val after = snapshot("""{"favor":4}""")
        val journal = preparedJournal(before, after)

        val decision = StateMutationPolicy.resume(
            journal = journal,
            expectedOperationId = journal.operationId,
            expectedEntityType = StateMutationJournal.ENTITY_CHARACTER,
            expectedTargetKey = "npc_zhouming",
            current = before
        )

        val plan = requireNotNull(decision.plan)
        assertTrue(plan.applyAfter)
        assertFalse(plan.alreadyCompleted)
    }

    @Test
    fun resume_marksPreparedJournalCompleteWhenEntityAlreadyMatchesAfter() {
        val before = snapshot("""{"favor":1}""")
        val after = snapshot("""{"favor":4}""")
        val journal = preparedJournal(before, after)

        val decision = StateMutationPolicy.resume(
            journal = journal,
            expectedOperationId = journal.operationId,
            expectedEntityType = StateMutationJournal.ENTITY_CHARACTER,
            expectedTargetKey = "npc_zhouming",
            current = after
        )

        val plan = requireNotNull(decision.plan)
        assertFalse(plan.applyAfter)
        assertTrue(plan.alreadyCompleted)
        assertEquals(StateMutationJournal.STATUS_COMPLETED, plan.journal.status)
    }

    @Test
    fun resume_completedJournalIgnoresLegitimateDownstreamEntityChanges() {
        val before = snapshot("""{"inventory":[{"name":"咖啡","quantity":1}]}""")
        val after = snapshot("""{"inventory":[{"name":"咖啡","quantity":2}]}""")
        val completed = StateMutationPolicy.markCompleted(preparedJournal(before, after))
        val downstream = snapshot("""{"inventory":[{"name":"咖啡","quantity":2},{"name":"彩票","quantity":1}]}""")

        val decision = StateMutationPolicy.resume(
            journal = completed,
            expectedOperationId = completed.operationId,
            expectedEntityType = StateMutationJournal.ENTITY_CHARACTER,
            expectedTargetKey = "npc_zhouming",
            current = downstream
        )

        val plan = requireNotNull(decision.plan)
        assertTrue(plan.alreadyCompleted)
        assertFalse(plan.applyAfter)
    }

    @Test
    fun resume_recognizesPreparedDeletionAndRejectsUnrelatedState() {
        val before = snapshot("""{"id":"event_1","name":"旧事件"}""")
        val after = StateMutationPolicy.Snapshot(exists = false)
        val journal = preparedJournal(
            before = before,
            after = after,
            entityType = StateMutationJournal.ENTITY_WORLDBOOK,
            targetKey = "event_1"
        )
        val deleted = StateMutationPolicy.resume(
            journal = journal,
            expectedOperationId = journal.operationId,
            expectedEntityType = StateMutationJournal.ENTITY_WORLDBOOK,
            expectedTargetKey = "event_1",
            current = after
        )
        val conflict = StateMutationPolicy.resume(
            journal = journal,
            expectedOperationId = journal.operationId,
            expectedEntityType = StateMutationJournal.ENTITY_WORLDBOOK,
            expectedTargetKey = "event_1",
            current = snapshot("""{"id":"event_1","name":"其他内容"}""")
        )

        assertTrue(requireNotNull(deleted.plan).alreadyCompleted)
        assertEquals(StateMutationPolicy.Failure.JOURNAL_CONFLICT, conflict.failure)
    }

    private fun preparedJournal(
        before: StateMutationPolicy.Snapshot,
        after: StateMutationPolicy.Snapshot,
        entityType: String = StateMutationJournal.ENTITY_CHARACTER,
        targetKey: String = "npc_zhouming"
    ): StateMutationJournal {
        return requireNotNull(
            StateMutationPolicy.prepare(
                round = 8,
                operationKey = "operation:$targetKey",
                entityType = entityType,
                targetKey = targetKey,
                before = before,
                after = after
            ).plan
        ).journal
    }

    private fun snapshot(json: String): StateMutationPolicy.Snapshot {
        return StateMutationPolicy.Snapshot(
            exists = true,
            state = JsonParser.parseString(json)
        )
    }
}
