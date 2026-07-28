package com.novelchat.data.model

import com.google.gson.annotations.SerializedName

data class WorldSetting(
    @SerializedName("name") val name: String = "",
    @SerializedName("genre") val genre: String = "",
    @SerializedName("tech_level") val techLevel: String = "",
    @SerializedName("magic_level") val magicLevel: String = "",
    @SerializedName("tone") val tone: String = "",
    @SerializedName("forbidden") val forbidden: List<String> = emptyList(),
    @SerializedName("description") val description: String = "",
    @SerializedName("lore") val lore: WorldLore = WorldLore()
)
