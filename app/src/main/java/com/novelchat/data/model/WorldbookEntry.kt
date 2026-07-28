package com.novelchat.data.model

import com.google.gson.annotations.SerializedName

data class WorldbookEntry(
    @SerializedName("id") val id: String = "",
    @SerializedName("type") val type: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("keywords") val keywords: List<String> = emptyList(),
    @SerializedName("related_characters") val relatedCharacters: List<String> = emptyList(),
    @SerializedName("related_events") val relatedEvents: List<String> = emptyList(),
    @SerializedName("related_locations") val relatedLocations: List<String> = emptyList(),
    @SerializedName("is_secret") val isSecret: Boolean = false,
    @SerializedName("known_by_player") val knownByPlayer: Boolean = true,
    @SerializedName("is_active") val isActive: Boolean = true,
    @SerializedName("activated_by") val activatedBy: String = "",
    @SerializedName("deactivated_by") val deactivatedBy: String = "",
    @SerializedName("status") val status: String = "正常",
    @SerializedName("status_history") val statusHistory: List<StatusHistory> = emptyList(),
    @SerializedName("created_by") val createdBy: String = "ai",
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("last_modified") val lastModified: String = ""
)

data class StatusHistory(
    @SerializedName("time") val time: String = "",
    @SerializedName("status") val status: String = "",
    @SerializedName("event") val event: String = "",
    @SerializedName("caused_by") val causedBy: String = ""
)
