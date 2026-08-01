package com.novelchat.ui.viewmodel

import com.novelchat.data.model.CharacterFieldDefinition
import com.novelchat.data.remote.model.ParsedNpc
import com.novelchat.data.remote.model.ParsedPlayer
import com.novelchat.data.remote.model.ParsedWorldSetting
import com.novelchat.data.remote.model.ParsedWorldbookItem
import com.novelchat.data.remote.model.StoryParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryParseQualityPolicyTest {

    @Test
    fun qualityIssue_rejectsEmptyOrGenericCharacterResults() {
        assertNotNull(StoryParseQualityPolicy.qualityIssue(StoryParseResult()))

        val issue = StoryParseQualityPolicy.qualityIssue(
            usableResult(
                player = ParsedPlayer(name = "主角", class_ = "记者"),
                npcs = listOf(ParsedNpc(name = "邻居A", occupation = "邻居"))
            )
        )

        assertTrue(issue.orEmpty().contains("具体姓名"))
        assertTrue(issue.orEmpty().contains("身份泛称"))
    }

    @Test
    fun qualityIssue_acceptsNamedStructuredResult() {
        assertNull(
            StoryParseQualityPolicy.qualityIssue(
                usableResult(
                    player = ParsedPlayer(name = "林克", class_ = "记者"),
                    npcs = listOf(ParsedNpc(name = "王常", occupation = "邻居"))
                )
            )
        )
    }

    @Test
    fun qualityIssue_requestsRepairWhenAiOmitsCharacterFieldContract() {
        val result = usableResult(
            player = ParsedPlayer(name = "林克", class_ = "记者"),
            npcs = listOf(ParsedNpc(name = "王常", occupation = "邻居"))
        ).copy(
            worldSetting = ParsedWorldSetting(name = "海临市", description = "当代城市生活。")
        )

        assertTrue(StoryParseQualityPolicy.qualityIssue(result).orEmpty().contains("character_fields"))
    }

    @Test
    fun qualityIssue_rejectsDuplicatePlayerAndNpcNames() {
        val playerCollision = StoryParseQualityPolicy.qualityIssue(
            usableResult(
                player = ParsedPlayer(name = "林克", class_ = "记者"),
                npcs = listOf(ParsedNpc(name = "林克", occupation = "邻居"))
            )
        )
        val npcCollision = StoryParseQualityPolicy.qualityIssue(
            usableResult(
                player = ParsedPlayer(name = "林克", class_ = "记者"),
                npcs = listOf(
                    ParsedNpc(name = "王常", occupation = "邻居"),
                    ParsedNpc(name = "王常", occupation = "店员")
                )
            )
        )

        assertTrue(playerCollision.orEmpty().contains("同一个姓名"))
        assertTrue(npcCollision.orEmpty().contains("同一个姓名"))
    }

    @Test
    fun concretePlayerName_replacesIdentityLabels() {
        assertEquals("林克", StoryParseQualityPolicy.concretePlayerName("主角"))
        assertEquals("许知南", StoryParseQualityPolicy.concretePlayerName("许知南"))
    }

    private fun usableResult(
        player: ParsedPlayer,
        npcs: List<ParsedNpc>
    ) = StoryParseResult(
        worldSetting = ParsedWorldSetting(
            name = "海临市",
            description = "当代城市生活。",
            characterFields = listOf(
                CharacterFieldDefinition(key = "stamina", label = "体力")
            )
        ),
        player = player,
        npcs = npcs,
        worldbook = listOf(
            ParsedWorldbookItem(type = "location", name = "云桥里", description = "老小区")
        )
    )
}
