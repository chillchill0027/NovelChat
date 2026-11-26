package com.novelchat.ui.viewmodel

import com.novelchat.data.remote.ApiEndpointPolicy

object SetupFlowPolicy {
    private const val MAX_CONNECTION_PREVIEW_LENGTH = 160

    fun canContinue(apiUrl: String, apiKey: String, model: String): Boolean {
        return validationError(apiUrl, apiKey, model) == null
    }

    fun validationError(apiUrl: String, apiKey: String, model: String): String? {
        return ApiEndpointPolicy.validationError(apiUrl, apiKey, model)
    }

    fun hasSuccessfulTest(testResult: String?): Boolean {
        return testResult?.startsWith("连接成功") == true
    }

    fun requiresApiKey(apiUrl: String): Boolean {
        return !ApiEndpointPolicy.isLocalEndpoint(apiUrl)
    }

    fun normalizeApiUrlDraft(value: String): String =
        ApiEndpointPolicy.sanitizeUrlInput(value).take(ApiEndpointPolicy.MAX_BASE_URL_LENGTH)

    fun normalizeApiKeyDraft(value: String): String = value.take(ApiEndpointPolicy.MAX_API_KEY_LENGTH)

    fun normalizeModelDraft(value: String): String = value.take(ApiEndpointPolicy.MAX_MODEL_LENGTH)

    fun connectionSuccessMessage(response: String?): String {
        val preview = UserVisibleTextPolicy.compact(
            rawText = response,
            maxLength = MAX_CONNECTION_PREVIEW_LENGTH,
            fallback = "OK"
        )
        return "连接成功！模型回复：$preview"
    }

    fun connectionFailureMessage(errorMessage: String?): String {
        return "连接失败：${UserVisibleTextPolicy.errorDetail(errorMessage)}"
    }
}
