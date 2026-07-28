package com.novelchat.data.model

import com.google.gson.annotations.SerializedName

data class NarrativeEntry(
    @SerializedName("turn") val turn: Int = 0,
    @SerializedName("narrative") val narrative: String = "",
    @SerializedName("user_input") val userInput: String = "",
    @SerializedName("created_at") val createdAt: String = ""
)

data class SummaryEntry(
    @SerializedName("turn") val turn: Int = 0,
    @SerializedName("summary") val summary: String = "",
    @SerializedName("created_at") val createdAt: String = ""
)
