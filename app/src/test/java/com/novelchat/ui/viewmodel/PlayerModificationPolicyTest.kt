package com.novelchat.ui.viewmodel

import com.novelchat.data.model.PlayerSkill
import com.novelchat.data.model.PlayerState
import com.novelchat.data.remote.model.PlayerModificationPatch
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerModificationPolicyTest {

    @Test
    fun apply_updatesFieldsWithoutRequiringName() {
        val current = PlayerState(
            name = "林克",
            age = 25,
            personality = "谨慎",
            background = "原背景"
        )

        val updated = PlayerModificationPolicy.apply(
            current,
            PlayerModificationPatch(personality = "果断", age = 27)
        )

        assertEquals("林克", updated.name)
        assertEquals(27, updated.age)
        assertEquals("果断", updated.personality)
        assertEquals("原背景", updated.background)
    }

    @Test
    fun apply_preservesExistingSkillLevelAndAddsNewSkill() {
        val current = PlayerState(
            skills = listOf(PlayerSkill(name = "观察", level = 3))
        )

        val updated = PlayerModificationPolicy.apply(
            current,
            PlayerModificationPatch(skills = listOf(" 观察 ", "谈判", "谈判"))
        )

        assertEquals(
            listOf(PlayerSkill(name = "观察", level = 3), PlayerSkill(name = "谈判", level = 1)),
            updated.skills
        )
    }

    @Test
    fun apply_ignoresBlankTextAndInvalidAge() {
        val current = PlayerState(name = "林克", age = 25, gender = "男")

        val updated = PlayerModificationPolicy.apply(
            current,
            PlayerModificationPatch(name = " ", age = 999, gender = "")
        )

        assertEquals(current, updated)
    }

    @Test
    fun apply_ignoresGenericIdentityAsName() {
        val current = PlayerState(name = "林克")

        val updated = PlayerModificationPolicy.apply(
            current,
            PlayerModificationPatch(name = "主角")
        )

        assertEquals("林克", updated.name)
    }

    @Test
    fun apply_boundsModelGeneratedProfileAndSkills() {
        val updated = PlayerModificationPolicy.apply(
            PlayerState(name = "林克"),
            PlayerModificationPatch(
                name = "名".repeat(100),
                background = "背".repeat(7_000),
                skills = List(40) { index -> "技能$index" + "长".repeat(100) }
            )
        )

        assertEquals(80, updated.name.length)
        assertEquals(6_000, updated.background.length)
        assertEquals(30, updated.skills.size)
        assertEquals(80, updated.skills.first().name.length)
    }
}
