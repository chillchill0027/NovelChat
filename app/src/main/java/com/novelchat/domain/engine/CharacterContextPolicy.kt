package com.novelchat.domain.engine

import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.CharacterReferencePolicy
import com.novelchat.data.model.NarrativeEntry

internal object CharacterContextPolicy {

    private const val MAX_STATE_SYNC_CHARACTERS = 12
    private const val MAX_NARRATIVE_CHARACTERS = 12

    private val rolePrefix = Regex(
        "^(房东|同事|邻居|招聘顾问|顾问|客户总监|店员|前台|医生|护士|保安|司机|老板)"
    )

    fun selectForNarrative(
        allCharacters: List<CharacterCard>,
        sceneCharacterNames: List<String>,
        userMessage: String,
        contextText: String = ""
    ): List<CharacterCard> {
        val selected = linkedMapOf<String, CharacterCard>()

        fun add(cards: Iterable<CharacterCard>) {
            cards.forEach { card ->
                val key = card.id.ifBlank { card.name }
                if (key.isNotBlank()) selected.putIfAbsent(key, card)
            }
        }

        add(referencesInText(allCharacters, userMessage))
        add(sceneCharacterNames.mapNotNull { CharacterReferencePolicy.resolve(allCharacters, it) })
        add(referencesInText(allCharacters, contextText))
        return selected.values.take(MAX_NARRATIVE_CHARACTERS)
    }

    fun selectForStateSync(
        allCharacters: List<CharacterCard>,
        narrativeCharacters: List<CharacterCard>,
        currentNarrative: String,
        recoveryNarratives: List<NarrativeEntry>
    ): List<CharacterCard> {
        val selected = linkedMapOf<String, CharacterCard>()

        fun add(cards: Iterable<CharacterCard>) {
            cards.forEach { card ->
                val key = card.id.ifBlank { card.name }
                if (key.isNotBlank()) selected.putIfAbsent(key, card)
            }
        }

        add(narrativeCharacters)
        add(referencesInText(allCharacters, currentNarrative))
        recoveryNarratives.asReversed().forEach { entry ->
            add(referencesInText(allCharacters, entry.userInput + "\n" + entry.narrative))
        }
        return selected.values.take(MAX_STATE_SYNC_CHARACTERS)
    }

    private fun referencesInText(
        allCharacters: List<CharacterCard>,
        text: String
    ): List<CharacterCard> {
        val roleNames = resolveRoleReferences(allCharacters, text)
        return allCharacters.filter { card -> card.name in roleNames || isReferenced(card, text) }
    }

    private fun resolveRoleReferences(
        allCharacters: List<CharacterCard>,
        text: String
    ): Set<String> {
        return roleReferences.asSequence()
            .filter { reference -> text.contains(reference, ignoreCase = true) }
            .mapNotNull { reference -> CharacterReferencePolicy.resolve(allCharacters, reference)?.name }
            .toSet()
    }

    private fun isReferenced(card: CharacterCard, userMessage: String): Boolean {
        if (card.name.isNotBlank() && userMessage.contains(card.name, ignoreCase = true)) return true
        val shortName = card.name.replaceFirst(rolePrefix, "")
        if (shortName.length >= 2 && userMessage.contains(shortName, ignoreCase = true)) return true

        val normalizedMessage = userMessage.lowercase().filter { it.isLetterOrDigit() }
        val normalizedId = card.id.lowercase().filter { it.isLetterOrDigit() }
        return normalizedId.length >= 3 && normalizedMessage.contains(normalizedId)
    }

    private val roleReferences = listOf(
        "女朋友", "女友", "男朋友", "男友", "妻子", "丈夫", "老婆", "老公", "恋人", "伴侣",
        "邻居", "房东", "同事", "招聘顾问", "顾问", "店主", "老板", "店员", "服务员", "前台",
        "医生", "护士", "保安", "司机"
    )
}
