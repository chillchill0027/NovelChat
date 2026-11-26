package com.novelchat.domain.v2.context

import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.CharacterFieldDefinition
import com.novelchat.data.model.CharacterLore
import com.novelchat.data.model.MemoryNote
import com.novelchat.data.model.MentalState
import com.novelchat.data.model.Relationship
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterSheetRendererTest {

    @Test
    fun full_rendersSpeechStyleSampleDialoguesMemoriesAndAuthorSecrets() {
        val card = CharacterCard(
            id = "zhou_ming",
            name = "周明",
            age = 44,
            occupation = "房东",
            personality = "精明、好面子，嘴硬心软",
            emotion = "烦躁",
            mentalState = MentalState(mood = "烦躁", currentThoughts = "刚和老婆吵过架"),
            relationships = mapOf("林克" to Relationship(favor = -10, trust = 20, stage = "催租中的紧张关系")),
            lore = CharacterLore(
                speechStyle = "本地口音，爱用反问",
                sampleDialogues = listOf("这个月又拖？", "最晚周五。"),
                secrets = listOf("他其实打算卖掉这套房子")
            ),
            memories = listOf(
                MemoryNote(turn = 2, text = "普通寒暄", weight = 1),
                MemoryNote(turn = 10, text = "林克帮他修过水管", weight = 2),
                MemoryNote(turn = 18, text = "林克答应周五交租", weight = 2),
                MemoryNote(turn = 19, text = "刚在楼道打招呼", weight = 1)
            ),
            customAttributes = mapOf("secret_debt" to "500")
        )
        val fields = listOf(
            CharacterFieldDefinition(
                key = "secret_debt",
                label = "隐藏债务",
                display = CharacterFieldDefinition.DISPLAY_HIDDEN,
                appliesTo = CharacterFieldDefinition.TARGET_NPC
            )
        )

        val rendered = CharacterSheetRenderer.full(
            card = card,
            playerName = "林克",
            characterFields = fields,
            currentRound = 20,
            maxMemories = 3
        )

        assertTrue(rendered.contains("### 周明（房东，44岁；与林克：催租中的紧张关系）"))
        assertTrue(rendered.contains("说话方式：本地口音，爱用反问"))
        assertTrue(rendered.contains("示例台词：「这个月又拖？」「最晚周五。」"))
        assertTrue(rendered.contains("好感 -10，信任 20/100"))
        assertTrue(rendered.contains("作者备注（角色不会主动透露）：他其实打算卖掉这套房子"))
        assertTrue(rendered.contains("林克答应周五交租（第18轮）"))
        assertFalse(rendered.contains("普通寒暄"))
        assertFalse(rendered.contains("隐藏债务"))
    }

    @Test
    fun full_omitsSpeechLinesWhenLegacyCardLacksThem() {
        val legacy = CharacterCard(name = "路平", occupation = "邻居")
        val rendered = CharacterSheetRenderer.full(legacy, "林克", emptyList(), currentRound = 1)

        assertFalse(rendered.contains("说话方式"))
        assertFalse(rendered.contains("示例台词"))
        assertFalse(rendered.contains("作者备注"))
    }
}
