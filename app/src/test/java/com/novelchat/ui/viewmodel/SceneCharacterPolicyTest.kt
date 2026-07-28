package com.novelchat.ui.viewmodel

import com.novelchat.data.model.CharacterCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneCharacterPolicyTest {

    private val cards = listOf(
        CharacterCard(id = "lin_yue", name = "同事林越"),
        CharacterCard(id = "meng_qi", name = "招聘顾问孟琪")
    )

    @Test
    fun visibleNpcCards_filtersToExplicitSceneCharacters() {
        val visible = SceneCharacterPolicy.visibleNpcCards(
            cards = cards,
            activeCharacters = listOf("林克", "同事林越"),
            playerName = "林克"
        )

        assertEquals(listOf("同事林越"), visible.map { it.name })
    }

    @Test
    fun visibleNpcCards_doesNotShowEveryNpcWhenPlayerIsAlone() {
        val visible = SceneCharacterPolicy.visibleNpcCards(
            cards = cards,
            activeCharacters = listOf("林克"),
            playerName = "林克"
        )

        assertTrue(visible.isEmpty())
    }

    @Test
    fun visibleNpcCards_keepsEmptySceneFreeOfUnrelatedCards() {
        val visible = SceneCharacterPolicy.visibleNpcCards(
            cards = cards,
            activeCharacters = emptyList(),
            playerName = "林克"
        )

        assertTrue(visible.isEmpty())
    }

    @Test
    fun visibleNpcCards_resolvesUniqueShortNameAlias() {
        val visible = SceneCharacterPolicy.visibleNpcCards(
            cards = cards,
            activeCharacters = listOf("林越"),
            playerName = "林克"
        )

        assertEquals(listOf("同事林越"), visible.map { it.name })
    }

    @Test
    fun visibleNpcCards_resolvesLegacyGenericNameToConcreteCard() {
        val visible = SceneCharacterPolicy.visibleNpcCards(
            cards = listOf(CharacterCard(id = "neighbor", name = "王常", occupation = "邻居")),
            activeCharacters = listOf("邻居A"),
            playerName = "林克"
        )

        assertEquals(listOf("王常"), visible.map { it.name })
    }

    @Test
    fun reconcileEmptyActiveCharacters_recoversNamedNpcFromFinalSentenceOnly() {
        val result = SceneCharacterPolicy.reconcileEmptyActiveCharacters(
            cards = listOf(
                CharacterCard(name = "苏晚"),
                CharacterCard(name = "周明")
            ),
            activeCharacters = emptyList(),
            playerName = "林克",
            latestNarrative = "林克想起苏晚的期待。周明取出兑奖登记册，朝林克递了过来。"
        )

        assertEquals(listOf("周明"), result)
    }

    @Test
    fun reconcileEmptyActiveCharacters_doesNotOverrideExplicitSceneRoster() {
        val result = SceneCharacterPolicy.reconcileEmptyActiveCharacters(
            cards = listOf(CharacterCard(name = "周明"), CharacterCard(name = "王常")),
            activeCharacters = listOf("王常"),
            playerName = "林克",
            latestNarrative = "周明递来登记册。"
        )

        assertEquals(listOf("王常"), result)
    }
}
