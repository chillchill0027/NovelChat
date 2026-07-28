package com.novelchat.data.model

import com.google.gson.annotations.SerializedName

data class StoryState(
    @SerializedName("current_round") val currentRound: Int = 0,
    @SerializedName("active_characters") val activeCharacters: List<String> = emptyList(),
    @SerializedName("current_location") val currentLocation: String = "",
    @SerializedName("time_of_day") val timeOfDay: String = "白天",
    @SerializedName("weather") val weather: String = "晴朗",
    @SerializedName("mood") val mood: String = "",
    @SerializedName("scene_description") val sceneDescription: String = "",
    @SerializedName("pending_events") val pendingEvents: List<String> = emptyList(),
    @SerializedName("global_flags") val globalFlags: Map<String, String> = emptyMap()
)
