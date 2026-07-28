package com.novelchat.data.remote.model

import com.google.gson.annotations.SerializedName

data class ApiProfile(
    @SerializedName("name") val name: String = "",
    @SerializedName("base_url") val baseUrl: String = "",
    @SerializedName("api_key") val apiKey: String = "",
    @SerializedName("model") val model: String = ""
)
