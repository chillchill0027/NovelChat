package com.novelchat.data.model

import com.google.gson.annotations.SerializedName

/**
 * Stable, author-controlled world details. Entity-level facts should still live in
 * worldbook entries so they can be loaded only when relevant to the current scene.
 */
data class WorldLore(
    @SerializedName("era") val era: String = "",
    @SerializedName("premise") val premise: String = "",
    @SerializedName("history") val history: String = "",
    @SerializedName("geography") val geography: String = "",
    @SerializedName("cosmology") val cosmology: String = "",
    @SerializedName("society") val society: String = "",
    @SerializedName("culture") val culture: String = "",
    @SerializedName("politics") val politics: String = "",
    @SerializedName("economy") val economy: String = "",
    @SerializedName("law") val law: String = "",
    @SerializedName("religion") val religion: String = "",
    @SerializedName("languages") val languages: List<String> = emptyList(),
    @SerializedName("calendar") val calendar: String = "",
    @SerializedName("power_system") val powerSystem: String = "",
    @SerializedName("technology_rules") val technologyRules: String = "",
    @SerializedName("factions") val factions: List<String> = emptyList(),
    @SerializedName("conflicts") val conflicts: List<String> = emptyList(),
    @SerializedName("rules") val rules: List<String> = emptyList(),
    @SerializedName("themes") val themes: List<String> = emptyList(),
    @SerializedName("narrative_style") val narrativeStyle: String = "",
    @SerializedName("custom") val custom: Map<String, String> = emptyMap()
) {
    fun hasContent(): Boolean {
        return listOf(
            era,
            premise,
            history,
            geography,
            cosmology,
            society,
            culture,
            politics,
            economy,
            law,
            religion,
            calendar,
            powerSystem,
            technologyRules,
            narrativeStyle
        ).any(String::isNotBlank) ||
            listOf(languages, factions, conflicts, rules, themes).any { it.isNotEmpty() } ||
            custom.isNotEmpty()
    }
}

/**
 * Long-lived characterization that should not be overwritten by short-term state updates.
 */
data class CharacterLore(
    @SerializedName("aliases") val aliases: List<String> = emptyList(),
    @SerializedName("role") val role: String = "",
    @SerializedName("pronouns") val pronouns: String = "",
    @SerializedName("birth_date") val birthDate: String = "",
    @SerializedName("nationality") val nationality: String = "",
    @SerializedName("residence") val residence: String = "",
    @SerializedName("education") val education: String = "",
    @SerializedName("values") val values: List<String> = emptyList(),
    @SerializedName("goals") val goals: List<String> = emptyList(),
    @SerializedName("motivations") val motivations: List<String> = emptyList(),
    @SerializedName("fears") val fears: List<String> = emptyList(),
    @SerializedName("weaknesses") val weaknesses: List<String> = emptyList(),
    @SerializedName("secrets") val secrets: List<String> = emptyList(),
    @SerializedName("habits") val habits: List<String> = emptyList(),
    @SerializedName("speech_style") val speechStyle: String = "",
    @SerializedName("sample_dialogues") val sampleDialogues: List<String> = emptyList(),
    @SerializedName("abilities") val abilities: List<String> = emptyList(),
    @SerializedName("knowledge") val knowledge: List<String> = emptyList(),
    @SerializedName("boundaries") val boundaries: List<String> = emptyList(),
    @SerializedName("character_arc") val characterArc: String = "",
    @SerializedName("relationship_notes") val relationshipNotes: Map<String, String> = emptyMap(),
    @SerializedName("custom") val custom: Map<String, String> = emptyMap()
) {
    fun hasContent(): Boolean {
        return listOf(
            role,
            pronouns,
            birthDate,
            nationality,
            residence,
            education,
            speechStyle,
            characterArc
        ).any(String::isNotBlank) ||
            listOf(
                aliases,
                values,
                goals,
                motivations,
                fears,
                weaknesses,
                secrets,
                habits,
                sampleDialogues,
                abilities,
                knowledge,
                boundaries
            ).any { it.isNotEmpty() } ||
            relationshipNotes.isNotEmpty() ||
            custom.isNotEmpty()
    }
}
