package com.novelchat.data.storage

import com.google.gson.Gson
import com.novelchat.data.remote.model.ApiStageRoutes

internal class ApiStageRoutesCodec(
    private val gson: Gson = Gson()
) {
    fun decode(json: String): ApiStageRoutes {
        if (json.isBlank()) return ApiStageRoutes()
        return runCatching {
            gson.fromJson(json, ApiStageRoutes::class.java) ?: ApiStageRoutes()
        }.getOrDefault(ApiStageRoutes())
    }

    fun encode(routes: ApiStageRoutes): String = gson.toJson(routes)
}
