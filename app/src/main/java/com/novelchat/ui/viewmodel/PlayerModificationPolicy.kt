package com.novelchat.ui.viewmodel

import com.novelchat.data.model.CharacterNamingPolicy
import com.novelchat.data.model.PlayerSkill
import com.novelchat.data.model.PlayerState
import com.novelchat.data.remote.model.PlayerModificationPatch

internal object PlayerModificationPolicy {
    private const val MAX_NAME_LENGTH = 80
    private const val MAX_SHORT_LENGTH = 120
    private const val MAX_PROFILE_LENGTH = 2_000
    private const val MAX_BACKGROUND_LENGTH = 6_000
    private const val MAX_SKILLS = 30

    fun apply(current: PlayerState, patch: PlayerModificationPatch): PlayerState {
        val skills = patch.skills?.let { requestedSkills ->
            requestedSkills
                .map { it.orEmpty().trim().take(MAX_NAME_LENGTH) }
                .filter { it.isNotBlank() }
                .distinct()
                .take(MAX_SKILLS)
                .map { name ->
                    current.skills.firstOrNull { it.name == name }
                        ?: PlayerSkill(name = name, level = 1)
                }
        } ?: current.skills

        return current.copy(
            name = patch.name
                ?.trim()
                ?.take(MAX_NAME_LENGTH)
                ?.takeIf { it.isNotBlank() && !CharacterNamingPolicy.isGenericPlayerName(it) }
                ?: current.name,
            age = patch.age?.takeIf { it in 1..120 } ?: current.age,
            gender = patch.gender.nonBlankOr(current.gender, MAX_SHORT_LENGTH),
            race = patch.race.nonBlankOr(current.race, MAX_SHORT_LENGTH),
            class_ = patch.class_.nonBlankOr(current.class_, MAX_SHORT_LENGTH),
            personality = patch.personality.nonBlankOr(current.personality, MAX_PROFILE_LENGTH),
            background = patch.background.nonBlankOr(current.background, MAX_BACKGROUND_LENGTH),
            appearance = patch.appearance.nonBlankOr(current.appearance, MAX_PROFILE_LENGTH),
            skills = skills
        )
    }

    private fun String?.nonBlankOr(fallback: String, maxLength: Int): String =
        this?.trim()?.take(maxLength)?.takeIf { it.isNotBlank() } ?: fallback
}
