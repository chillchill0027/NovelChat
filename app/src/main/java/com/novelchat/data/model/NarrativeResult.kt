package com.novelchat.data.model

import com.google.gson.annotations.SerializedName

data class NarrativeResult(
    @SerializedName("narrative") val narrative: String = "",
    @SerializedName("summary") val summary: String = "",
    @SerializedName("action_coverage") val actionCoverage: List<ActionCoverage> = emptyList(),
    @SerializedName("forward_hook") val forwardHook: ForwardHook = ForwardHook(),
    @SerializedName("ending_scene") val endingScene: EndingScene = EndingScene(),
    @SerializedName("introduced_characters") val introducedCharacters: List<IntroducedCharacter> = emptyList()
)

data class ActionCoverage(
    @SerializedName("action") val action: String = "",
    @SerializedName("evidence") val evidence: String = ""
)

data class ForwardHook(
    @SerializedName("cause") val cause: String = "",
    @SerializedName("evidence") val evidence: String = ""
)

data class EndingScene(
    @SerializedName("location") val location: String = "",
    @SerializedName("present_characters") val presentCharacters: List<String>? = null
)

data class IntroducedCharacter(
    @SerializedName("name") val name: String = "",
    @SerializedName("occupation") val occupation: String = "",
    @SerializedName("relationship_to_player") val relationshipToPlayer: String = "",
    @SerializedName("first_appearance") val firstAppearance: String = ""
)
