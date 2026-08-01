package com.novelchat.domain.engine

import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.EndingScene
import com.novelchat.data.model.IntroducedCharacter
import com.novelchat.data.model.NarrativeResult
import org.junit.Assert.assertEquals
import org.junit.Test

class NarrativeCharacterGroundingPolicyTest {

    @Test
    fun excludePlayerFromEndingScene_removesPlayerAndDuplicatesBeforeValidation() {
        val result = NarrativeCharacterGroundingPolicy.excludePlayerFromEndingScene(
            result = NarrativeResult(
                endingScene = EndingScene(
                    "办公室",
                    listOf("沈默", "主角", "陆砚", "陆砚")
                )
            ),
            playerName = "沈默"
        )

        assertEquals(listOf("陆砚"), result.endingScene.presentCharacters)
    }

    @Test
    fun reconcile_removesNamesThatNeverAppearInFinalNarrative() {
        val result = NarrativeCharacterGroundingPolicy.reconcile(
            result = NarrativeResult(
                narrative = "一位蓝制服男士走来问候。",
                endingScene = EndingScene("省福彩中心", listOf("王奶奶", "张经理")),
                introducedCharacters = listOf(
                    IntroducedCharacter("张经理", "福彩中心工作人员", "初识", "主动迎接林克")
                )
            ),
            narrative = "林克走进省福彩中心，一位蓝制服男士走来问候。",
            knownCharacters = listOf(CharacterCard(name = "王奶奶", occupation = "早餐摊主")),
            playerName = "林克"
        )

        assertEquals(emptyList<IntroducedCharacter>(), result.introducedCharacters)
        assertEquals(emptyList<String>(), result.endingScene.presentCharacters)
    }

    @Test
    fun reconcile_keepsNormalizedPlaceholderWhenNameAppearsInNarrative() {
        val result = NarrativeCharacterGroundingPolicy.reconcile(
            result = NarrativeResult(
                endingScene = EndingScene("兑奖窗口", listOf("店员A")),
                introducedCharacters = listOf(
                    IntroducedCharacter("店员A", "福彩中心工作人员", "初识", "核验中奖彩票")
                )
            ),
            narrative = "店员阿远接过彩票，在林克面前逐项核验号码。",
            knownCharacters = emptyList(),
            playerName = "林克"
        )

        assertEquals(listOf("阿远"), result.introducedCharacters.map { it.name })
        assertEquals(listOf("阿远"), result.endingScene.presentCharacters)
    }
}
