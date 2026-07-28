package com.novelchat.data.model

object CharacterIdentityPolicy {

    data class RosterResult(
        val cards: List<CharacterCard>,
        val aliases: Map<String, String>
    )

    fun findHighConfidenceAlias(
        cards: List<CharacterCard>,
        candidate: CharacterCard
    ): CharacterCard? {
        return cards.filter { existing ->
            isHighConfidenceAlias(existing, candidate)
        }.singleOrNull()
    }

    fun isHighConfidenceAlias(first: CharacterCard, second: CharacterCard): Boolean {
        if (first.name.isBlank() || second.name.isBlank() || first.name == second.name) return false
        return isBreakfastVendor(first) &&
            isBreakfastVendor(second) &&
            isElderly(first) &&
            isElderly(second) &&
            (hasSparseState(first) || hasSparseState(second))
    }

    fun preferMoreComplete(first: CharacterCard, second: CharacterCard): CharacterCard {
        val firstScore = completenessScore(first)
        val secondScore = completenessScore(second)
        return when {
            firstScore > secondScore -> first
            secondScore > firstScore -> second
            first.lastModified > second.lastModified -> first
            else -> second
        }
    }

    fun reconcileRoster(cards: List<CharacterCard>): RosterResult {
        val reconciled = cards.toMutableList()
        val aliases = linkedMapOf<String, String>()

        for (firstIndex in reconciled.indices) {
            for (secondIndex in firstIndex + 1 until reconciled.size) {
                val first = reconciled[firstIndex]
                val second = reconciled[secondIndex]
                if (!isHighConfidenceAlias(first, second)) continue

                val preferred = preferMoreComplete(first, second)
                val firstWins = preferred === first
                val winnerIndex = if (firstWins) firstIndex else secondIndex
                val loserIndex = if (firstWins) secondIndex else firstIndex
                val winner = reconciled[winnerIndex]
                val loser = reconciled[loserIndex]
                val activeStatus = if (winner.status == "active" || loser.status == "active") {
                    "active"
                } else {
                    winner.status
                }

                reconciled[winnerIndex] = winner.copy(status = activeStatus)
                reconciled[loserIndex] = loser.copy(status = "pool")
                aliases[loser.name] = winner.name
            }
        }

        return RosterResult(cards = reconciled, aliases = aliases)
    }

    private fun isBreakfastVendor(card: CharacterCard): Boolean {
        val text = identityText(card)
        return breakfastMarkers.any(text::contains)
    }

    private fun isElderly(card: CharacterCard): Boolean {
        val text = identityText(card)
        return elderlyMarkers.any(text::contains)
    }

    private fun hasSparseState(card: CharacterCard): Boolean {
        val mental = card.mentalState
        val relationshipsUnchanged = card.relationships.values.all { relationship ->
            relationship.favor == 0 && relationship.trust == 0
        }
        return mental.mood.isBlank() &&
            mental.focus.isBlank() &&
            mental.currentThoughts.isBlank() &&
            relationshipsUnchanged &&
            card.inventory.isEmpty()
    }

    private fun completenessScore(card: CharacterCard): Int {
        var score = 0
        if (card.occupation.isNotBlank()) score += 1
        if (card.personality.isNotBlank() && card.personality != "待观察") score += 1
        if (card.background.isNotBlank()) score += 1
        if (card.appearance.isNotBlank()) score += 1
        if (card.emotion.isNotBlank() && card.emotion != "平静") score += 2
        if (card.mentalState.mood.isNotBlank()) score += 2
        if (card.mentalState.focus.isNotBlank()) score += 2
        if (card.mentalState.currentThoughts.isNotBlank()) score += 2
        if (card.relationships.values.any { it.favor != 0 || it.trust != 0 }) score += 3
        if (card.inventory.isNotEmpty()) score += 1
        return score
    }

    private fun identityText(card: CharacterCard): String = buildString {
        append(card.name)
        append(card.occupation)
        append(card.background)
        append(card.appearance)
    }

    private val breakfastMarkers = listOf("早餐摊", "早餐店", "早点摊", "早点店")
    private val elderlyMarkers = listOf(
        "老奶奶", "奶奶", "老太太", "老婆婆", "阿婆", "年迈", "老人",
        "老爷爷", "爷爷", "老大爷"
    )
}
