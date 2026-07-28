package com.novelchat.data.model

object NarrativeResultContentPolicy {
    const val MAX_NARRATIVE_LENGTH = 40_000
    const val MAX_SUMMARY_LENGTH = 1_200
    const val MAX_ACTION_COVERAGE = 12
    const val MAX_ACTION_LENGTH = 240
    const val MAX_EVIDENCE_LENGTH = 300
    const val MAX_HOOK_CAUSE_LENGTH = 500
    const val MAX_LOCATION_LENGTH = 160
    const val MAX_PRESENT_CHARACTERS = 24
    const val MAX_INTRODUCED_CHARACTERS = 24
    const val MAX_CHARACTER_NAME_LENGTH = 80
    const val MAX_OCCUPATION_LENGTH = 120
    const val MAX_RELATIONSHIP_LENGTH = 120
    const val MAX_FIRST_APPEARANCE_LENGTH = 600

    fun normalize(result: NarrativeResult): NarrativeResult {
        return result.copy(
            narrative = result.narrative.trim().take(MAX_NARRATIVE_LENGTH),
            summary = result.summary.trim().take(MAX_SUMMARY_LENGTH),
            actionCoverage = result.actionCoverage
                .map { coverage ->
                    coverage.copy(
                        action = coverage.action.trim().take(MAX_ACTION_LENGTH),
                        evidence = coverage.evidence.trim().take(MAX_EVIDENCE_LENGTH)
                    )
                }
                .distinctBy { coverage -> coverage.action to coverage.evidence }
                .take(MAX_ACTION_COVERAGE),
            forwardHook = result.forwardHook.copy(
                cause = result.forwardHook.cause.trim().take(MAX_HOOK_CAUSE_LENGTH),
                evidence = result.forwardHook.evidence.trim().take(MAX_EVIDENCE_LENGTH)
            ),
            endingScene = result.endingScene.copy(
                location = result.endingScene.location.trim().take(MAX_LOCATION_LENGTH),
                presentCharacters = result.endingScene.presentCharacters?.let(::normalizeNames)
            ),
            introducedCharacters = result.introducedCharacters
                .map { character ->
                    character.copy(
                        name = character.name.trim().take(MAX_CHARACTER_NAME_LENGTH),
                        occupation = character.occupation.trim().take(MAX_OCCUPATION_LENGTH),
                        relationshipToPlayer = character.relationshipToPlayer.trim()
                            .take(MAX_RELATIONSHIP_LENGTH),
                        firstAppearance = character.firstAppearance.trim()
                            .take(MAX_FIRST_APPEARANCE_LENGTH)
                    )
                }
                .filter { character ->
                    character.name.isNotBlank() || character.occupation.isNotBlank() ||
                        character.relationshipToPlayer.isNotBlank() || character.firstAppearance.isNotBlank()
                }
                .distinctBy { character -> character.name.lowercase() to character.occupation.lowercase() }
                .take(MAX_INTRODUCED_CHARACTERS)
        )
    }

    private fun normalizeNames(names: List<String>): List<String> {
        return names
            .map { name -> name.trim().take(MAX_CHARACTER_NAME_LENGTH) }
            .filter(String::isNotBlank)
            .distinctBy(String::lowercase)
            .take(MAX_PRESENT_CHARACTERS)
    }
}
