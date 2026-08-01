package com.novelchat.domain.engine

import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.CharacterNamingPolicy
import com.novelchat.data.model.CharacterReferencePolicy
import com.novelchat.data.model.IntroducedCharacter
import com.novelchat.data.model.NarrativeResult

internal object NarrativeCharacterGroundingPolicy {

    fun excludePlayerFromEndingScene(
        result: NarrativeResult,
        playerName: String
    ): NarrativeResult {
        val presentCharacters = result.endingScene.presentCharacters ?: return result
        val filtered = presentCharacters
            .map(String::trim)
            .filter { it.isNotBlank() && it != playerName && it != "主角" }
            .distinct()
        return result.copy(
            endingScene = result.endingScene.copy(presentCharacters = filtered)
        )
    }

    fun reconcile(
        result: NarrativeResult,
        narrative: String,
        knownCharacters: List<CharacterCard>,
        playerName: String
    ): NarrativeResult {
        val groundedIntroductions = result.introducedCharacters
            .map(::normalizeIntroduction)
            .filter { character ->
                character.name.isNotBlank() && character.name != playerName &&
                    narrative.contains(character.name)
            }
            .distinctBy { it.name }

        val roster = knownCharacters + groundedIntroductions.map { character ->
            CharacterCard(name = character.name, occupation = character.occupation)
        }
        val groundedPresentCharacters = result.endingScene.presentCharacters.orEmpty()
            .mapNotNull { reference ->
                resolveGroundedName(reference, roster, narrative)
            }
            .filter { it != playerName }
            .distinct()

        return result.copy(
            introducedCharacters = groundedIntroductions,
            endingScene = result.endingScene.copy(
                presentCharacters = groundedPresentCharacters
            )
        )
    }

    private fun normalizeIntroduction(character: IntroducedCharacter): IntroducedCharacter {
        val normalizedCard = CharacterNamingPolicy.normalizeNpcCard(
            CharacterCard(name = character.name, occupation = character.occupation)
        )
        return character.copy(
            name = normalizedCard.name,
            occupation = character.occupation.ifBlank { normalizedCard.occupation }
        )
    }

    private fun resolveGroundedName(
        reference: String,
        roster: List<CharacterCard>,
        narrative: String
    ): String? {
        val normalizedReference = CharacterNamingPolicy.normalizeNpcCard(
            CharacterCard(name = reference)
        ).name
        val resolved = roster.singleOrNull { it.name == normalizedReference }?.name
            ?: CharacterReferencePolicy.resolve(roster, reference)?.name
            ?: roster.singleOrNull { it.name == reference.trim() }?.name
            ?: return null
        return resolved.takeIf(narrative::contains)
    }
}
