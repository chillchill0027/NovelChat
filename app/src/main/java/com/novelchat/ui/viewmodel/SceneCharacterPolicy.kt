package com.novelchat.ui.viewmodel

import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.CharacterReferencePolicy

internal object SceneCharacterPolicy {

    fun reconcileEmptyActiveCharacters(
        cards: List<CharacterCard>,
        activeCharacters: List<String>,
        playerName: String,
        latestNarrative: String
    ): List<String> {
        if (activeCharacters.any(String::isNotBlank)) return activeCharacters
        val finalSentence = latestNarrative
            .split(Regex("[。！？!?\\n]+"))
            .map(String::trim)
            .lastOrNull(String::isNotBlank)
            .orEmpty()
            .takeLast(FINAL_SENTENCE_WINDOW)
        if (finalSentence.isBlank()) return activeCharacters
        return cards.asSequence()
            .filter { it.name.isNotBlank() && it.name != playerName && it.status == "active" }
            .map(CharacterCard::name)
            .filter(finalSentence::contains)
            .distinct()
            .toList()
    }

    fun visibleNpcCards(
        cards: List<CharacterCard>,
        activeCharacters: List<String>,
        playerName: String
    ): List<CharacterCard> {
        val npcCards = cards.filter { it.name != playerName }
        val activeNames = activeCharacters.filter { it.isNotBlank() }
        if (activeNames.isEmpty()) return emptyList()

        val resolvedNames = activeNames.mapNotNull { reference ->
            CharacterReferencePolicy.resolve(npcCards, reference)?.name
        }.toSet()
        return npcCards.filter { it.name in resolvedNames }
    }

    private const val FINAL_SENTENCE_WINDOW = 600
}
