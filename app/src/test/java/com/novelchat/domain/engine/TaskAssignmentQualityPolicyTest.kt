package com.novelchat.domain.engine

import com.novelchat.data.model.CharacterFieldDefinition
import com.novelchat.data.model.EventRecord
import com.novelchat.data.model.PhysicalStatePatch
import com.novelchat.data.model.PlayerChanges
import com.novelchat.data.model.PlayerUpdate
import com.novelchat.data.model.RelationshipStatePatch
import com.novelchat.data.model.StoryStateUpdate
import com.novelchat.data.model.TaskAssignment
import com.novelchat.data.model.WorldSetting
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskAssignmentQualityPolicyTest {

    @Test
    fun meaningful_acceptsPersistentEventOnlyAssignment() {
        assertTrue(
            TaskAssignmentQualityPolicy.isMeaningful(
                TaskAssignment(
                    events = listOf(
                        EventRecord(
                            type = "兑奖",
                            target = "彩票店",
                            description = "店员完成中奖彩票核验"
                        )
                    )
                )
            )
        )
    }

    @Test
    fun isMeaningful_rejectsEmptyShells() {
        assertFalse(TaskAssignmentQualityPolicy.isMeaningful(TaskAssignment()))
        assertFalse(
            TaskAssignmentQualityPolicy.isMeaningful(
                TaskAssignment(
                    storyUpdate = StoryStateUpdate(),
                    playerUpdate = PlayerUpdate(changes = PlayerChanges())
                )
            )
        )
        assertFalse(
            TaskAssignmentQualityPolicy.isMeaningful(
                TaskAssignment(playerUpdate = PlayerUpdate(reason = "只是解释，没有变化"))
            )
        )
        assertFalse(
            TaskAssignmentQualityPolicy.isMeaningful(
                TaskAssignment(
                    playerUpdate = PlayerUpdate(
                        changes = PlayerChanges(
                            relationships = mapOf("苏晚" to RelationshipStatePatch())
                        )
                    )
                )
            )
        )
    }

    @Test
    fun isMeaningful_acceptsExplicitSceneClearsAndRiskZero() {
        assertTrue(
            TaskAssignmentQualityPolicy.isMeaningful(
                TaskAssignment(storyUpdate = StoryStateUpdate(activeCharacters = emptyList()))
            )
        )
        assertTrue(
            TaskAssignmentQualityPolicy.isMeaningful(
                TaskAssignment(playerUpdate = PlayerUpdate(changes = PlayerChanges(risk = 0)))
            )
        )
    }

    @Test
    fun isMeaningful_acceptsOnlyExplicitlyCertifiedNoChange() {
        assertTrue(
            TaskAssignmentQualityPolicy.isMeaningful(
                TaskAssignment(
                    syncStatus = "no_change",
                    noChangeReason = "本轮只延续了对话，场景和人物状态均未改变"
                )
            )
        )
        assertFalse(
            TaskAssignmentQualityPolicy.isMeaningful(
                TaskAssignment(syncStatus = "no_change", noChangeReason = "无")
            )
        )
    }

    @Test
    fun isMeaningful_rejectsEventOnlyAssignmentBecauseItDoesNotPersistState() {
        assertFalse(
            TaskAssignmentQualityPolicy.isMeaningful(
                TaskAssignment(
                    events = listOf(
                        com.novelchat.data.model.EventRecord(description = "店员核验了中奖彩票")
                    )
                )
            )
        )
    }

    @Test
    fun dynamicUpdates_requireRiskWhenExposureEventIsExplicit() {
        val world = WorldSetting(
            characterFields = listOf(
                CharacterFieldDefinition(
                    key = "risk",
                    label = "秘仪暴露",
                    description = "违规调查被学院发现的综合概率",
                    valueType = CharacterFieldDefinition.TYPE_NUMBER,
                    appliesTo = CharacterFieldDefinition.TARGET_PLAYER,
                    updateMode = CharacterFieldDefinition.UPDATE_DYNAMIC
                )
            )
        )

        val missing = TaskAssignmentQualityPolicy.missingDynamicPlayerUpdates(
            worldSetting = world,
            narrative = "警报骤然响起，巡逻队开始搜查闯入者。",
            assignment = TaskAssignment(storyUpdate = StoryStateUpdate(currentLocation = "排水沟"))
        )

        assertTrue("秘仪暴露(risk)" in missing)
    }

    @Test
    fun dynamicUpdates_acceptExplicitFinalValueAndIgnoreUntriggeredFields() {
        val world = WorldSetting(
            characterFields = listOf(
                CharacterFieldDefinition(
                    key = "stamina",
                    label = "魔力储备",
                    description = "施法消耗，休息恢复",
                    valueType = CharacterFieldDefinition.TYPE_NUMBER,
                    updateMode = CharacterFieldDefinition.UPDATE_DYNAMIC
                ),
                CharacterFieldDefinition(
                    key = "risk",
                    label = "秘仪暴露",
                    description = "违规行动被发现的概率",
                    valueType = CharacterFieldDefinition.TYPE_NUMBER,
                    updateMode = CharacterFieldDefinition.UPDATE_DYNAMIC
                )
            )
        )
        val assignment = TaskAssignment(
            playerUpdate = PlayerUpdate(
                changes = PlayerChanges(physicalState = PhysicalStatePatch(stamina = 132))
            )
        )

        assertTrue(
            TaskAssignmentQualityPolicy.missingDynamicPlayerUpdates(
                world,
                "祁越完成施法，周围一切安静。",
                assignment
            ).isEmpty()
        )
    }

    @Test
    fun dynamicUpdates_requireCustomAttributeForTriggeredContract() {
        val world = WorldSetting(
            characterFields = listOf(
                CharacterFieldDefinition(
                    key = "spell_control",
                    label = "咒式稳定",
                    description = "施法时维持咒式结构的稳定程度",
                    valueType = CharacterFieldDefinition.TYPE_NUMBER,
                    updateMode = CharacterFieldDefinition.UPDATE_DYNAMIC
                )
            )
        )

        assertTrue(
            TaskAssignmentQualityPolicy.missingDynamicPlayerUpdates(
                world,
                "祁越施展侦测术，咒式在潮声中摇晃。",
                TaskAssignment(storyUpdate = StoryStateUpdate(currentLocation = "灯塔"))
            ).contains("咒式稳定(spell_control)")
        )
    }

    @Test
    fun dynamicUpdates_ignoreExplicitlyNegatedTrigger() {
        val world = WorldSetting(
            characterFields = listOf(
                CharacterFieldDefinition(
                    key = "risk",
                    label = "秘仪暴露",
                    description = "违规行动被发现的概率",
                    valueType = CharacterFieldDefinition.TYPE_NUMBER,
                    updateMode = CharacterFieldDefinition.UPDATE_DYNAMIC
                )
            )
        )

        assertFalse(
            TaskAssignmentQualityPolicy.missingDynamicPlayerUpdates(
                world,
                "祁越及时收手，警报没有响起，也没有巡逻队追来。",
                TaskAssignment(storyUpdate = StoryStateUpdate(currentLocation = "宿舍"))
            ).contains("秘仪暴露(risk)")
        )
    }

    @Test
    fun narrativeConsistency_requiresRecoveryFieldsAndTimeAfterSleepingUntilDawn() {
        val world = WorldSetting(
            characterFields = listOf(
                CharacterFieldDefinition(
                    key = "stamina",
                    label = "魔力储备",
                    description = "施法消耗，休息或睡眠恢复",
                    valueType = CharacterFieldDefinition.TYPE_NUMBER,
                    updateMode = CharacterFieldDefinition.UPDATE_DYNAMIC
                ),
                CharacterFieldDefinition(
                    key = "fatigue",
                    label = "身体疲劳",
                    description = "行动累积并在睡眠后缓解",
                    updateMode = CharacterFieldDefinition.UPDATE_DYNAMIC
                )
            )
        )

        val error = runCatching {
            TaskAssignmentQualityPolicy.requireNarrativeConsistency(
                world,
                "祁越在宿舍入睡，直到晨光照进房间才醒来。",
                TaskAssignment(storyUpdate = StoryStateUpdate(currentLocation = "307室"))
            )
        }.exceptionOrNull() as IncompleteStateSyncException

        assertTrue("魔力储备(stamina)" in error.missingFields)
        assertTrue("身体疲劳(fatigue)" in error.missingFields)
        assertTrue("场景时间(time_of_day)" in error.missingFields)
    }

    @Test
    fun narrativeConsistency_usesEnglishActionButIgnoresResolvedAlarm() {
        val world = WorldSetting(
            characterFields = listOf(
                CharacterFieldDefinition(
                    key = "stamina",
                    label = "魔力储备",
                    description = "施法消耗，休息或睡眠恢复",
                    valueType = CharacterFieldDefinition.TYPE_NUMBER,
                    updateMode = CharacterFieldDefinition.UPDATE_DYNAMIC
                ),
                CharacterFieldDefinition(
                    key = "fatigue",
                    label = "身体疲劳",
                    description = "持续行动造成的疲劳程度",
                    updateMode = CharacterFieldDefinition.UPDATE_DYNAMIC
                ),
                CharacterFieldDefinition(
                    key = "risk",
                    label = "秘仪暴露",
                    description = "被警报或巡逻发现的概率",
                    valueType = CharacterFieldDefinition.TYPE_NUMBER,
                    updateMode = CharacterFieldDefinition.UPDATE_DYNAMIC
                )
            )
        )
        val context = """
            I return to dormitory and sleep until dawn.
            警报声彻底平息，堤道上再无巡逻队。清晨，祁越在307室醒来。
        """.trimIndent()

        val error = runCatching {
            TaskAssignmentQualityPolicy.requireNarrativeConsistency(
                world,
                context,
                TaskAssignment(storyUpdate = StoryStateUpdate(currentLocation = "307室"))
            )
        }.exceptionOrNull() as IncompleteStateSyncException

        assertTrue("魔力储备(stamina)" in error.missingFields)
        assertTrue("身体疲劳(fatigue)" in error.missingFields)
        assertTrue("场景时间(time_of_day)" in error.missingFields)
        assertFalse("秘仪暴露(risk)" in error.missingFields)
    }
}
