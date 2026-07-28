package com.novelchat.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NarrativeResultContentPolicyTest {

    @Test
    fun normalize_boundsEveryModelControlledNarrativeField() {
        val result = NarrativeResultContentPolicy.normalize(
            NarrativeResult(
                narrative = "  " + "正文".repeat(25_000) + "  ",
                summary = "摘要".repeat(1_000),
                actionCoverage = List(20) { index ->
                    ActionCoverage(
                        action = " 行动$index" + "长".repeat(300),
                        evidence = " 证据$index" + "长".repeat(400)
                    )
                },
                forwardHook = ForwardHook(
                    cause = "原因".repeat(400),
                    evidence = "证据".repeat(300)
                ),
                endingScene = EndingScene(
                    location = "地点".repeat(100),
                    presentCharacters = List(30) { " 人物$it " }
                ),
                introducedCharacters = List(30) { index ->
                    IntroducedCharacter(
                        name = " 姓名$index" + "长".repeat(100),
                        occupation = "职业".repeat(100),
                        relationshipToPlayer = "关系".repeat(100),
                        firstAppearance = "首次登场".repeat(200)
                    )
                }
            )
        )

        assertEquals(NarrativeResultContentPolicy.MAX_NARRATIVE_LENGTH, result.narrative.length)
        assertEquals(NarrativeResultContentPolicy.MAX_SUMMARY_LENGTH, result.summary.length)
        assertEquals(NarrativeResultContentPolicy.MAX_ACTION_COVERAGE, result.actionCoverage.size)
        assertTrue(result.actionCoverage.all { it.action.length <= 240 && it.evidence.length <= 300 })
        assertEquals(NarrativeResultContentPolicy.MAX_HOOK_CAUSE_LENGTH, result.forwardHook.cause.length)
        assertEquals(NarrativeResultContentPolicy.MAX_EVIDENCE_LENGTH, result.forwardHook.evidence.length)
        assertEquals(NarrativeResultContentPolicy.MAX_LOCATION_LENGTH, result.endingScene.location.length)
        assertEquals(NarrativeResultContentPolicy.MAX_PRESENT_CHARACTERS, result.endingScene.presentCharacters?.size)
        assertEquals(NarrativeResultContentPolicy.MAX_INTRODUCED_CHARACTERS, result.introducedCharacters.size)
        assertTrue(result.introducedCharacters.all {
            it.name.length <= 80 && it.occupation.length <= 120 &&
                it.relationshipToPlayer.length <= 120 && it.firstAppearance.length <= 600
        })
    }
}
