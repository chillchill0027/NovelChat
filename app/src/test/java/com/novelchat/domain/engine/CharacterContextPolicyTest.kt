package com.novelchat.domain.engine

import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.NarrativeEntry
import com.novelchat.data.model.defaultStoryCharacters
import com.novelchat.data.model.defaultStoryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterContextPolicyTest {

    private val characters = listOf(
        CharacterCard(id = "lin_yue", name = "同事林越"),
        CharacterCard(id = "meng_qi", name = "招聘顾问孟琪"),
        CharacterCard(id = "zhang_wei", name = "张伟")
    )

    @Test
    fun selectForNarrative_includesSceneAndExplicitlyReferencedOffSceneCharacter() {
        val selected = CharacterContextPolicy.selectForNarrative(
            allCharacters = characters,
            sceneCharacterNames = listOf("张伟"),
            userMessage = "I call Lin Yue into the meeting room."
        )

        assertEquals(listOf("同事林越", "张伟"), selected.map { it.name })
    }

    @Test
    fun selectForNarrative_matchesChineseNameWithoutRolePrefix() {
        val selected = CharacterContextPolicy.selectForNarrative(
            allCharacters = characters,
            sceneCharacterNames = listOf("张伟"),
            userMessage = "我让孟琪稍后给我回电话。"
        )

        assertEquals(listOf("招聘顾问孟琪", "张伟"), selected.map { it.name })
    }

    @Test
    fun selectForNarrative_emptySceneIncludesOnlyExplicitReferences() {
        val selected = CharacterContextPolicy.selectForNarrative(
            allCharacters = characters,
            sceneCharacterNames = emptyList(),
            userMessage = "我独自在茶水间喝水。"
        )

        assertEquals(emptyList<CharacterCard>(), selected)
    }

    @Test
    fun selectForNarrative_resolvesSceneAliasWithoutRolePrefix() {
        val selected = CharacterContextPolicy.selectForNarrative(
            allCharacters = characters,
            sceneCharacterNames = listOf("林越"),
            userMessage = "我继续整理会议材料。"
        )

        assertEquals(listOf("同事林越"), selected.map { it.name })
    }

    @Test
    fun selectForNarrative_resolvesUniqueRelationshipAliasToExistingName() {
        val girlfriend = CharacterCard(
            id = "su_wan",
            name = "苏晚",
            occupation = "林克的女朋友"
        )

        val selected = CharacterContextPolicy.selectForNarrative(
            allCharacters = characters + girlfriend,
            sceneCharacterNames = emptyList(),
            userMessage = "安置好女朋友之后，我下楼买咖啡。"
        )

        assertEquals(listOf("苏晚"), selected.map { it.name })
    }

    @Test
    fun selectForStateSync_prioritizesRelevantCharactersAndBoundsPromptRoster() {
        val roster = (1..20).map { index ->
            CharacterCard(id = "character_$index", name = "角色$index")
        }
        val currentNarrative = roster.joinToString("，") { it.name }

        val selected = CharacterContextPolicy.selectForStateSync(
            allCharacters = roster,
            narrativeCharacters = listOf(roster.last()),
            currentNarrative = currentNarrative,
            recoveryNarratives = listOf(
                NarrativeEntry(turn = 3, narrative = "角色19 在上一轮递交了资料。")
            )
        )

        assertEquals("角色20", selected.first().name)
        assertEquals(12, selected.size)
        assertTrue(selected.map { it.name }.contains("角色1"))
    }

    @Test
    fun selectForStateSync_includesRecoveryReferencesButExcludesUnrelatedCards() {
        val current = CharacterCard(id = "current", name = "苏晚")
        val recovery = CharacterCard(id = "recovery", name = "王常")
        val unrelated = CharacterCard(id = "unrelated", name = "许知南")

        val selected = CharacterContextPolicy.selectForStateSync(
            allCharacters = listOf(current, recovery, unrelated),
            narrativeCharacters = listOf(current),
            currentNarrative = "苏晚留在家中休息。",
            recoveryNarratives = listOf(
                NarrativeEntry(turn = 2, narrative = "王常收下了林克代缴的水费。")
            )
        )

        assertEquals(listOf("苏晚", "王常"), selected.map { it.name })
    }

    @Test
    fun selectForNarrative_usesPendingEventReferencesWithoutUnboundedRoster() {
        val roster = (1..20).map { index ->
            CharacterCard(id = "npc_$index", name = "人物$index")
        }
        val pendingContext = roster.joinToString("；") { card -> "等待${card.name}回复" }

        val selected = CharacterContextPolicy.selectForNarrative(
            allCharacters = roster,
            sceneCharacterNames = emptyList(),
            userMessage = "开始这一天",
            contextText = pendingContext
        )

        assertEquals(12, selected.size)
    }

    @Test
    fun selectForNarrative_defaultOpeningIncludesEveryPresentCharacter() {
        val openingState = defaultStoryState()
        val selected = CharacterContextPolicy.selectForNarrative(
            allCharacters = defaultStoryCharacters(createdAt = "2026-11-27T00:00:00Z"),
            sceneCharacterNames = openingState.activeCharacters,
            userMessage = "抱拳行礼，环顾演武场众人",
            contextText = openingState.pendingEvents.joinToString("\n")
        )

        assertEquals(
            listOf("赵志敬", "鹿清笃", "甄志丙"),
            selected.take(3).map { it.name }
        )
    }

    @Test
    fun selectForNarrative_prioritizesCurrentMessageThenSceneBeforeBackground() {
        val background = (1..18).map { index ->
            CharacterCard(id = "background_$index", name = "背景人物$index")
        }
        val current = CharacterCard(id = "current", name = "沈知遥")
        val scene = CharacterCard(id = "scene", name = "顾明川")
        val roster = background + current + scene

        val selected = CharacterContextPolicy.selectForNarrative(
            allCharacters = roster,
            sceneCharacterNames = listOf(scene.name),
            userMessage = "我先给沈知遥回电话。",
            contextText = background.joinToString("；") { "等待${it.name}回复" }
        )

        assertEquals(listOf(current.name, scene.name), selected.take(2).map { it.name })
        assertEquals(12, selected.size)
    }
}
