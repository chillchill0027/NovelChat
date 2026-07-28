package com.novelchat.domain.engine

import com.novelchat.data.model.NarrativeEntry
import com.novelchat.data.model.StoryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StateSyncPolicyTest {

    @Test
    fun markPending_keepsEarliestUnsyncedRound() {
        val first = StateSyncPolicy.markPending(StoryState(currentRound = 3), 3)
        val second = StateSyncPolicy.markPending(first.copy(currentRound = 4), 4)

        assertEquals(3, StateSyncPolicy.pendingFromRound(second))
    }

    @Test
    fun recoveryNarratives_selectsRoundsFromPendingMarker() {
        val state = StateSyncPolicy.markPending(StoryState(currentRound = 5), 3)
        val narratives = (1..5).map { NarrativeEntry(turn = it, narrative = "第${it}轮") }

        assertEquals(listOf(3, 4, 5), StateSyncPolicy.recoveryNarratives(state, narratives).map { it.turn })
    }

    @Test
    fun clearPending_preservesUnrelatedFlags() {
        val marked = StateSyncPolicy.markPending(
            StoryState(globalFlags = mapOf("branch" to "north")),
            2
        )

        val cleared = StateSyncPolicy.clearPending(marked)

        assertNull(StateSyncPolicy.pendingFromRound(cleared))
        assertEquals("north", cleared.globalFlags["branch"])
    }

    @Test
    fun reconcilePersistedNarratives_recoversRoundAndMarksUnsyncedNarrative() {
        val state = StoryState(currentRound = 2)
        val narratives = listOf(1, 2, 3, 4).map { turn ->
            NarrativeEntry(turn = turn, narrative = "round $turn")
        }

        val recovered = StateSyncPolicy.reconcilePersistedNarratives(state, narratives)

        assertEquals(4, recovered.currentRound)
        assertEquals(3, StateSyncPolicy.pendingFromRound(recovered))
    }

    @Test
    fun reconcilePersistedNarratives_keepsStateWhenRoundIsCurrent() {
        val state = StateSyncPolicy.markPending(StoryState(currentRound = 4), 4)
        val narratives = listOf(NarrativeEntry(turn = 4, narrative = "saved"))

        assertEquals(state, StateSyncPolicy.reconcilePersistedNarratives(state, narratives))
    }
}
