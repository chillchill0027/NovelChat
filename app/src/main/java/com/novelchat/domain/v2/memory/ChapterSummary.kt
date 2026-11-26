package com.novelchat.domain.v2.memory

import com.google.gson.annotations.SerializedName

data class ChapterSummary(
    @SerializedName("index") val index: Int = 0,
    @SerializedName("from_turn") val fromTurn: Int = 0,
    @SerializedName("to_turn") val toTurn: Int = 0,
    @SerializedName("text") val text: String = "",
    @SerializedName("is_overview") val isOverview: Boolean = false,
    @SerializedName("created_at") val createdAt: String = ""
)
