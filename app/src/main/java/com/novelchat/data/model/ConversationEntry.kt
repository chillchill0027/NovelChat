package com.novelchat.data.model

import com.google.gson.annotations.SerializedName

data class ConversationEntry(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("character_name") val characterName: String = "",
    @SerializedName("character_desc") val characterDesc: String = "",
    @SerializedName("type") val type: String = "preset",
    @SerializedName("preset_key") val presetKey: String = "",
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("last_message_at") val lastMessageAt: String = "",
    @SerializedName("message_count") val messageCount: Int = 0,
    @SerializedName("last_message") val lastMessage: String = ""
)
