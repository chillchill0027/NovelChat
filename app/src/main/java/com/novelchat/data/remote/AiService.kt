package com.novelchat.data.remote

import android.content.Context
import com.google.gson.Gson
import com.novelchat.data.remote.api.OpenAiApi
import com.novelchat.data.remote.model.ChatMessage
import com.novelchat.data.remote.model.ChatRequest
import com.novelchat.data.remote.model.ChatResponse
import com.novelchat.data.storage.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.Reader
import java.util.concurrent.TimeUnit

class AiService(private val context: Context) {

    private val gson = Gson()
    private val settingsRepository = SettingsRepository(context)
    private var currentApi: OpenAiApi? = null
    private var currentBaseUrl: String? = null
    private val apiMutex = Mutex()

    suspend fun isLocalEndpointConfigured(): Boolean {
        return ApiEndpointPolicy.isLocalEndpoint(settingsRepository.apiBaseUrl.first())
    }

    private suspend fun getApi(baseUrlOverride: String? = null): OpenAiApi {
        val configuredBaseUrl = baseUrlOverride?.trim()
            ?: settingsRepository.apiBaseUrl.first().trim()
        if (configuredBaseUrl.isBlank()) throw Exception("请先在设置中配置 API 地址")
        val baseUrl = ApiEndpointPolicy.normalizeBaseUrl(configuredBaseUrl) + "/"
        return apiMutex.withLock {
            if (currentApi == null || currentBaseUrl != baseUrl) {
                val logging = HttpLoggingInterceptor().apply {
                    redactHeader("Authorization")
                    level = HttpLoggingInterceptor.Level.BASIC
                }
                val client = OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(180, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .addInterceptor(logging)
                    .build()

                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build()

                currentApi = retrofit.create(OpenAiApi::class.java)
                currentBaseUrl = baseUrl
            }
            currentApi ?: error("API 客户端初始化失败")
        }
    }

    /**
     * SSE 流式调用，用于剧情生成
     */
    fun chatStream(
        messages: List<ChatMessage>,
        model: String? = null,
        temperature: Float = 0.8f,
        maxTokens: Int? = null,
        jsonMode: Boolean = false,
        structuredOutputKind: StructuredOutputKind = StructuredOutputKind.GENERIC_OBJECT
    ): Flow<String> = flow {
        val baseUrl = settingsRepository.apiBaseUrl.first().trim()
        val apiKey = settingsRepository.apiKey.first().trim()
        val selectedModel = (model ?: settingsRepository.model.first()).trim()
        ApiEndpointPolicy.requireValid(baseUrl, apiKey, selectedModel)
        val api = getApi(baseUrl)
        val authorization = apiKey.takeIf { it.isNotBlank() }?.let { "Bearer $it" }

        val request = ChatRequest(
            model = selectedModel.ifBlank { SettingsRepository.DEFAULT_MODEL },
            messages = messages,
            stream = true,
            temperature = temperature,
            maxTokens = maxTokens,
            responseFormat = StructuredOutputFormatPolicy
                .responseFormat(baseUrl, structuredOutputKind)
                .takeIf { jsonMode },
            reasoningEffort = ChatRequestPolicy.reasoningEffort(baseUrl, selectedModel),
            thinking = ChatRequestPolicy.thinkingConfig(baseUrl, selectedModel)
        )

        val response = executeRequestWithCompatibilityFallback(
            api = api,
            authorization = authorization,
            request = request,
            baseUrl = baseUrl
        )

        val responseBody = response.body() ?: throw Exception("响应体为空")
        var finishReason: String? = null
        var receivedContent = false
        var receivedChars = 0
        var sawDone = false
        var malformed = false
        var serverError = false
        var nonStreamingResponse = false
        val decoder = ChatStreamDecoder()
        val consumeEvents: suspend (List<ChatStreamEvent>) -> Unit = { events ->
            for (event in events) {
                finishReason = event.finishReason ?: finishReason
                sawDone = sawDone || event.done
                malformed = malformed || event.malformed
                serverError = serverError || event.serverError
                nonStreamingResponse = nonStreamingResponse || event.nonStreamingResponse
                val content = event.content
                if (!content.isNullOrEmpty()) {
                    receivedChars = ApiResponsePolicy.nextTotal(receivedChars, content.length)
                    receivedContent = true
                    emit(content)
                }
            }
        }
        coroutineScope {
            val closeOnCancel = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    responseBody.close()
                }
            }
            try {
                BufferedReader(InputStreamReader(responseBody.byteStream())).use { bufferedReader ->
                    val lineReader = BoundedSseLineReader(bufferedReader)
                    var line: String?
                    while (lineReader.readLine().also { line = it } != null) {
                        currentCoroutineContext().ensureActive()
                        val currentLine = line ?: continue
                        consumeEvents(decoder.acceptLine(currentLine))
                        if (sawDone) break
                    }
                    consumeEvents(decoder.finish())
                }
            } finally {
                closeOnCancel.cancel()
                responseBody.close()
            }
        }
        ChatStreamCompletionPolicy.requireComplete(
            receivedContent = receivedContent,
            sawDone = sawDone,
            finishReason = finishReason,
            malformed = malformed,
            serverError = serverError,
            nonStreamingResponse = nonStreamingResponse,
            jsonMode = jsonMode
        )
    }.flowOn(Dispatchers.IO)

    /**
     * 非流式调用，返回完整文本（用于任务分配、状态更新等）
     */
    suspend fun chatComplete(
        messages: List<ChatMessage>,
        model: String? = null,
        temperature: Float = 0.3f,
        maxTokens: Int? = null,
        jsonMode: Boolean = false,
        structuredOutputKind: StructuredOutputKind = StructuredOutputKind.GENERIC_OBJECT
    ): String = withContext(Dispatchers.IO) {
        val baseUrl = settingsRepository.apiBaseUrl.first().trim()
        val apiKey = settingsRepository.apiKey.first().trim()
        val selectedModel = (model ?: settingsRepository.model.first()).trim()
        ApiEndpointPolicy.requireValid(baseUrl, apiKey, selectedModel)
        val api = getApi(baseUrl)
        completeRequest(
            api = api,
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = selectedModel,
            messages = messages,
            temperature = temperature,
            maxTokens = maxTokens,
            jsonMode = jsonMode,
            structuredOutputKind = structuredOutputKind
        )
    }

    suspend fun testConnection(
        baseUrl: String,
        apiKey: String,
        model: String
    ): String = withContext(Dispatchers.IO) {
        ApiEndpointPolicy.requireValid(baseUrl, apiKey, model)
        val normalizedBaseUrl = ApiEndpointPolicy.normalizeBaseUrl(baseUrl)
        val api = getApi(normalizedBaseUrl)
        completeRequest(
            api = api,
            baseUrl = normalizedBaseUrl,
            apiKey = apiKey.trim(),
            model = model.trim(),
            messages = listOf(ChatMessage(role = "user", content = "只回复 OK，不要解释。")),
            temperature = 0f,
            maxTokens = 64,
            jsonMode = false
        )
    }

    private suspend fun completeRequest(
        api: OpenAiApi,
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        temperature: Float,
        maxTokens: Int?,
        jsonMode: Boolean,
        structuredOutputKind: StructuredOutputKind = StructuredOutputKind.GENERIC_OBJECT
    ): String {
        val authorization = apiKey.takeIf { it.isNotBlank() }?.let { "Bearer $it" }
        val request = ChatRequest(
            model = model.ifBlank { SettingsRepository.DEFAULT_MODEL },
            messages = messages,
            stream = false,
            temperature = temperature,
            maxTokens = maxTokens,
            responseFormat = StructuredOutputFormatPolicy
                .responseFormat(baseUrl, structuredOutputKind)
                .takeIf { jsonMode },
            reasoningEffort = ChatRequestPolicy.reasoningEffort(baseUrl, model),
            thinking = ChatRequestPolicy.thinkingConfig(baseUrl, model)
        )
        val response = executeRequestWithCompatibilityFallback(
            api = api,
            authorization = authorization,
            request = request,
            baseUrl = baseUrl
        )
        val body = response.body() ?: throw Exception("响应体为空")
        val responseText = readLimited(
            reader = body.charStream(),
            maxChars = ApiResponsePolicy.MAX_RESPONSE_CHARS + 1
        ).orEmpty()
        ApiResponsePolicy.requireCompleteLength(responseText.length)
        val jsonResponse = gson.fromJson(responseText, ChatResponse::class.java)
            ?: throw Exception("AI 响应为空")
        return ChatCompletionPolicy.requireContent(jsonResponse, jsonMode)
    }

    private suspend fun executeRequestWithRetry(
        api: OpenAiApi,
        authorization: String?,
        request: ChatRequest
    ): retrofit2.Response<okhttp3.ResponseBody> {
        var lastNetworkError: IOException? = null
        repeat(ApiRetryPolicy.MAX_ATTEMPTS) { attempt ->
            val response = try {
                api.chatCompletions(authorization = authorization, request = request)
            } catch (e: IOException) {
                lastNetworkError = e
                if (attempt < ApiRetryPolicy.MAX_ATTEMPTS - 1) {
                    delay(ApiRetryPolicy.delayMillis(null, attempt))
                    return@repeat
                }
                throw e
            }

            if (response.isSuccessful) return response

            val retryAfter = response.headers()["Retry-After"]
            val errorBody = readLimited(response.errorBody()?.charStream())
            if (
                ApiRetryPolicy.isTransientStatus(response.code()) &&
                attempt < ApiRetryPolicy.MAX_ATTEMPTS - 1
            ) {
                delay(ApiRetryPolicy.delayMillis(retryAfter, attempt))
                return@repeat
            }
            throw ApiHttpException(
                statusCode = response.code(),
                responseBody = errorBody,
                safeMessage = ApiErrorPolicy.message(response.code(), errorBody)
            )
        }
        throw lastNetworkError ?: IOException("API 请求未完成")
    }

    private suspend fun executeRequestWithCompatibilityFallback(
        api: OpenAiApi,
        authorization: String?,
        request: ChatRequest,
        baseUrl: String
    ): retrofit2.Response<okhttp3.ResponseBody> {
        var compatibleRequest = request
        while (true) {
            try {
                return executeRequestWithRetry(api, authorization, compatibleRequest)
            } catch (error: ApiHttpException) {
                compatibleRequest = when {
                    compatibleRequest.reasoningEffort != null &&
                        ReasoningEffortFallbackPolicy.shouldRetryWithoutReasoningEffort(
                            baseUrl = baseUrl,
                            statusCode = error.statusCode,
                            errorBody = error.responseBody
                        ) -> compatibleRequest.copy(reasoningEffort = null)

                    compatibleRequest.responseFormat != null &&
                        StructuredOutputFallbackPolicy.shouldRetryWithoutJsonMode(
                            baseUrl = baseUrl,
                            statusCode = error.statusCode,
                            errorBody = error.responseBody
                        ) -> compatibleRequest.copy(responseFormat = null)

                    else -> throw error
                }
            }
        }
    }

    private fun readLimited(reader: Reader?, maxChars: Int = 4_096): String? {
        if (reader == null) return null
        return reader.buffered().use { buffered ->
            val result = StringBuilder(minOf(maxChars, 1_024))
            val chunk = CharArray(1_024)
            while (result.length < maxChars) {
                val count = buffered.read(
                    chunk,
                    0,
                    minOf(chunk.size, maxChars - result.length)
                )
                if (count < 0) break
                result.append(chunk, 0, count)
            }
            result.toString()
        }
    }

    private class ApiHttpException(
        val statusCode: Int,
        val responseBody: String?,
        safeMessage: String
    ) : Exception(safeMessage)
}
