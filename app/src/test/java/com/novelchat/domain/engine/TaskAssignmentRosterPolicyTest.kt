package com.novelchat.domain.engine

import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.CharacterUpdateRequest
import com.novelchat.data.model.IntroducedCharacter
import com.novelchat.data.model.NewCharacter
import com.novelchat.data.model.StoryStateUpdate
import com.novelchat.data.model.TaskAssignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskAssignmentRosterPolicyTest {

    @Test
    fun reconcile_createsEveryIntroducedCharacterAndResolvesGenericSceneRole() {
        val result = TaskAssignmentRosterPolicy.reconcile(
            assignment = TaskAssignment(
                storyUpdate = StoryStateUpdate(activeCharacters = listOf("彩票店老板")),
                newCharacters = listOf(
                    NewCharacter(name = "苏晚", occupation = "学生或职员", relationshipToPlayer = "初识")
                )
            ),
            knownCharacters = emptyList(),
            introducedCharacters = listOf(
                IntroducedCharacter(
                    name = "老周",
                    occupation = "彩票店老板",
                    firstAppearance = "老周接过彩票进行核验"
                ),
                IntroducedCharacter(
                    name = "苏晚",
                    occupation = "插画师",
                    relationshipToPlayer = "女朋友",
                    firstAppearance = "苏晚在家休息"
                )
            ),
            playerName = "林克"
        )

        assertEquals(listOf("老周"), result.storyUpdate?.activeCharacters)
        assertEquals(setOf("老周", "苏晚"), result.newCharacters.map { it.name }.toSet())
        assertEquals("女朋友", result.newCharacters.single { it.name == "苏晚" }.relationshipToPlayer)
        assertEquals("插画师", result.newCharacters.single { it.name == "苏晚" }.occupation)
    }

    @Test
    fun reconcile_doesNotDuplicateKnownCharacter() {
        val result = TaskAssignmentRosterPolicy.reconcile(
            assignment = TaskAssignment(
                storyUpdate = StoryStateUpdate(activeCharacters = listOf("彩票店老板")),
                newCharacters = listOf(NewCharacter(name = "彩票店老板", occupation = "彩票店老板"))
            ),
            knownCharacters = listOf(CharacterCard(name = "老周", occupation = "彩票店老板")),
            introducedCharacters = listOf(
                IntroducedCharacter(name = "老周", occupation = "彩票店老板")
            ),
            playerName = "林克"
        )

        assertEquals(listOf("老周"), result.storyUpdate?.activeCharacters)
        assertTrue(result.newCharacters.isEmpty())
    }

    @Test
    fun reconcile_mapsGenericIntroductionToUniqueKnownRole() {
        val result = TaskAssignmentRosterPolicy.reconcile(
            assignment = TaskAssignment(
                storyUpdate = StoryStateUpdate(activeCharacters = listOf("彩票店老板"))
            ),
            knownCharacters = listOf(CharacterCard(name = "老周", occupation = "彩票店老板")),
            introducedCharacters = listOf(
                IntroducedCharacter(name = "彩票店老板", occupation = "彩票店老板")
            ),
            playerName = "林克"
        )

        assertEquals(listOf("老周"), result.storyUpdate?.activeCharacters)
        assertTrue(result.newCharacters.isEmpty())
    }

    @Test
    fun reconcile_materializesUnresolvedActiveCharacterInsteadOfLeavingDanglingName() {
        val result = TaskAssignmentRosterPolicy.reconcile(
            assignment = TaskAssignment(
                storyUpdate = StoryStateUpdate(activeCharacters = listOf("店员"))
            ),
            knownCharacters = emptyList(),
            introducedCharacters = emptyList(),
            playerName = "林克"
        )

        assertEquals(listOf("阿远"), result.storyUpdate?.activeCharacters)
        assertEquals("阿远", result.newCharacters.single().name)
        assertEquals("店员", result.newCharacters.single().occupation)
    }

    @Test
    fun reconcile_retiresSparseRoleAliasWhenLaterNarrativeConfirmsNamedCharacter() {
        val result = TaskAssignmentRosterPolicy.reconcile(
            assignment = TaskAssignment(
                storyUpdate = StoryStateUpdate(activeCharacters = listOf("王奶奶"))
            ),
            knownCharacters = listOf(
                CharacterCard(
                    name = "陈玉兰",
                    occupation = "早餐摊贩",
                    appearance = "年迈，穿着朴素"
                )
            ),
            introducedCharacters = listOf(
                IntroducedCharacter(
                    name = "王奶奶",
                    occupation = "早餐摊店主",
                    firstAppearance = "王奶奶在早餐摊前关心地询问林克"
                )
            ),
            playerName = "林克"
        )

        assertEquals(listOf("王奶奶"), result.storyUpdate?.activeCharacters)
        assertEquals(listOf("王奶奶"), result.newCharacters.map { it.name })
        assertEquals(listOf("陈玉兰"), result.retiredCharacters.map { it.name })
    }

    @Test
    fun reconcile_dropsCharactersAndUpdatesWithoutFinalNarrativeEvidence() {
        val result = TaskAssignmentRosterPolicy.reconcile(
            assignment = TaskAssignment(
                storyUpdate = StoryStateUpdate(activeCharacters = listOf("王奶奶", "张经理")),
                newCharacters = listOf(
                    NewCharacter(name = "张经理", occupation = "福彩中心工作人员")
                ),
                characterUpdates = listOf(
                    CharacterUpdateRequest("王奶奶", "注意到林克离开"),
                    CharacterUpdateRequest("张经理", "主动迎接林克")
                )
            ),
            knownCharacters = listOf(CharacterCard(name = "王奶奶", occupation = "早餐摊店主")),
            introducedCharacters = emptyList(),
            playerName = "林克",
            narrativeContext = "林克走进省福彩中心，一位穿蓝制服的男士朝他走来。"
        )

        assertEquals(emptyList<String>(), result.storyUpdate?.activeCharacters)
        assertTrue(result.newCharacters.isEmpty())
        assertTrue(result.characterUpdates.isEmpty())
    }

    @Test
    fun reconcile_keepsNamedCharacterGroundedInFinalNarrative() {
        val result = TaskAssignmentRosterPolicy.reconcile(
            assignment = TaskAssignment(
                storyUpdate = StoryStateUpdate(activeCharacters = listOf("张衡")),
                newCharacters = listOf(
                    NewCharacter(name = "张衡", occupation = "福彩中心工作人员")
                ),
                characterUpdates = listOf(
                    CharacterUpdateRequest("张衡", "接过林克的彩票")
                )
            ),
            knownCharacters = emptyList(),
            introducedCharacters = emptyList(),
            playerName = "林克",
            narrativeContext = "工作人员张衡叫到林克的号码，接过中奖彩票开始核验。"
        )

        assertEquals(listOf("张衡"), result.storyUpdate?.activeCharacters)
        assertEquals(listOf("张衡"), result.newCharacters.map { it.name })
        assertEquals(listOf("张衡"), result.characterUpdates.map { it.name })
    }

    @Test
    fun reconcile_keepsNewCharacterInSceneWhenSmallModelReturnsEmptyRoster() {
        val narrative = """
            周明引导林克走向A07柜台。

            到了柜台旁，周明从托盘中取出兑奖登记册，朝林克递了过来。
        """.trimIndent()
        val result = TaskAssignmentRosterPolicy.reconcile(
            assignment = TaskAssignment(
                storyUpdate = StoryStateUpdate(activeCharacters = emptyList()),
                newCharacters = listOf(
                    NewCharacter(name = "周明", occupation = "福彩中心柜台专员")
                )
            ),
            knownCharacters = emptyList(),
            introducedCharacters = emptyList(),
            playerName = "林克",
            narrativeContext = narrative,
            currentNarrative = narrative
        )

        assertEquals(listOf("周明"), result.storyUpdate?.activeCharacters)
        assertEquals(listOf("周明"), result.newCharacters.map { it.name })
    }

    @Test
    fun reconcile_keepsKnownCharacterInSceneWhenSmallModelReturnsEmptyRoster() {
        val narrative = """
            林克跟着周明来到A07柜台。

            周明把登记册推到林克面前，请他填写姓名和身份证号码。
        """.trimIndent()
        val result = TaskAssignmentRosterPolicy.reconcile(
            assignment = TaskAssignment(
                storyUpdate = StoryStateUpdate(activeCharacters = emptyList())
            ),
            knownCharacters = listOf(
                CharacterCard(name = "周明", occupation = "柜台专员")
            ),
            introducedCharacters = emptyList(),
            playerName = "林克",
            narrativeContext = narrative,
            currentNarrative = narrative
        )

        assertEquals(listOf("周明"), result.storyUpdate?.activeCharacters)
    }

    @Test
    fun reconcile_doesNotTreatFutureArrivalAsPhysicalScenePresence() {
        val narrative = """
            李静把兑奖方式确认单推到林克面前，请他核对账户信息。周明在林克身侧轻轻点头。

            林克想到，既然苏晚马上会到，不如等她抵达后再商量。李静没有催促，只把笔放在确认单旁边。
        """.trimIndent()
        val result = TaskAssignmentRosterPolicy.reconcile(
            assignment = TaskAssignment(
                storyUpdate = StoryStateUpdate(
                    activeCharacters = listOf("周明", "苏晚", "李静")
                )
            ),
            knownCharacters = listOf(
                CharacterCard(name = "周明", occupation = "柜台专员"),
                CharacterCard(name = "苏晚", occupation = "插画师"),
                CharacterCard(name = "李静", occupation = "A07窗口工作人员")
            ),
            introducedCharacters = emptyList(),
            playerName = "林克",
            narrativeContext = narrative,
            currentNarrative = narrative
        )

        assertEquals(listOf("周明", "李静"), result.storyUpdate?.activeCharacters)
    }

    @Test
    fun reconcile_dropsPlayerAndUnknownCharacterUpdatesAndCanonicalizesKnownRole() {
        val narrative = """
            周明称呼林克为林先生，并把登记册递给他。
            柜台专员周明继续说明兑奖流程。
        """.trimIndent()
        val result = TaskAssignmentRosterPolicy.reconcile(
            assignment = TaskAssignment(
                characterUpdates = listOf(
                    CharacterUpdateRequest("林克", "主角准备填写信息"),
                    CharacterUpdateRequest("林先生", "主角确认材料齐全"),
                    CharacterUpdateRequest("工作人员", "继续引导林克"),
                    CharacterUpdateRequest("登记册", "出现在柜台上")
                )
            ),
            knownCharacters = listOf(
                CharacterCard(
                    name = "周明",
                    occupation = "柜台专员",
                    background = "福彩中心工作人员"
                )
            ),
            introducedCharacters = emptyList(),
            playerName = "林克",
            narrativeContext = narrative,
            currentNarrative = narrative
        )

        assertEquals(listOf("周明"), result.characterUpdates.map { it.name })
        assertEquals("继续引导林克", result.characterUpdates.single().reason)
    }

    @Test
    fun relationshipStatusFor_usesConfirmedRomanticRelationshipInsteadOfSingleDefault() {
        assertEquals("恋爱中", TaskAssignmentRosterPolicy.relationshipStatusFor("女朋友"))
        assertEquals("已婚", TaskAssignmentRosterPolicy.relationshipStatusFor("妻子"))
        assertEquals("未知", TaskAssignmentRosterPolicy.relationshipStatusFor("初识"))
    }
}
