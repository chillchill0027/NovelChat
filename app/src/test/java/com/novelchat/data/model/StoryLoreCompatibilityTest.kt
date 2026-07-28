package com.novelchat.data.model

import com.google.gson.Gson
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryLoreCompatibilityTest {
    private val gson = Gson()

    @Test
    fun oldWorldAndCharacterJson_receiveEmptyLoreDefaults() {
        val world = gson.fromJson(
            """{"name":"海临市","description":"旧存档"}""",
            WorldSetting::class.java
        )
        val player = gson.fromJson(
            """{"name":"林克","class":"记者"}""",
            PlayerState::class.java
        )
        val npc = gson.fromJson(
            """{"id":"顾宁","name":"顾宁","occupation":"设计师"}""",
            CharacterCard::class.java
        )

        assertFalse(world.lore.hasContent())
        assertFalse(player.lore.hasContent())
        assertFalse(npc.lore.hasContent())
    }

    @Test
    fun detailedLore_roundTripsThroughStoredModels() {
        val source = CharacterCard(
            id = "顾宁",
            name = "顾宁",
            lore = CharacterLore(
                goals = listOf("完成项目"),
                relationshipNotes = mapOf("林克" to "交往三年")
            )
        )

        val restored = gson.fromJson(gson.toJson(source), CharacterCard::class.java)

        assertTrue(restored.lore.hasContent())
        assertTrue(restored.lore.goals.contains("完成项目"))
    }
}
