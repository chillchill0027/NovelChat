package com.novelchat.domain.engine

import com.google.gson.Gson
import com.novelchat.data.model.CharacterLore
import com.novelchat.data.model.PlayerState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerPromptSnapshotPolicyTest {

    @Test
    fun create_usesNeutralDecisionUsefulFieldNames() {
        val json = Gson().toJson(
            PlayerPromptSnapshotPolicy.create(
                PlayerState(
                    name = "林克",
                    class_ = "城市租客",
                    huntingSkill = 42,
                    alpha = 53,
                    resistance = 61,
                    cv = 18,
                    lore = CharacterLore(
                        goals = listOf("在新岗位站稳"),
                        speechStyle = "先问关键事实"
                    )
                )
            )
        )

        assertTrue(json.contains("\"name\":\"林克\""))
        assertTrue(json.contains("\"occupation\":\"城市租客\""))
        assertTrue(json.contains("\"action_skill\":42"))
        assertTrue(json.contains("\"willpower\":53"))
        assertTrue(json.contains("\"resilience\":61"))
        assertTrue(json.contains("\"current_risk\":18"))
        assertTrue(json.contains("\"goals\":[\"在新岗位站稳\"]"))
        assertTrue(json.contains("\"speech_style\":\"先问关键事实\""))
        assertFalse(json.contains("\"hunting_skill\""))
        assertFalse(json.contains("\"alpha\""))
        assertFalse(json.contains("\"cv\""))
    }
}
