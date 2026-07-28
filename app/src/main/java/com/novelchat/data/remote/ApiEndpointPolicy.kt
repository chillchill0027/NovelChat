package com.novelchat.data.remote

import java.net.URI

internal object ApiEndpointPolicy {
    const val MAX_BASE_URL_LENGTH = 2_048
    const val MAX_API_KEY_LENGTH = 4_096
    const val MAX_MODEL_LENGTH = 256

    fun validationError(baseUrl: String, apiKey: String, model: String): String? {
        val normalizedUrl = baseUrl.trim()
        if (normalizedUrl.isBlank()) return "API 地址不能为空"
        if (model.trim().isBlank()) return "模型名称不能为空"
        if (normalizedUrl.length > MAX_BASE_URL_LENGTH) return "API 地址过长"
        if (apiKey.trim().length > MAX_API_KEY_LENGTH) return "API 密钥过长"
        if (model.trim().length > MAX_MODEL_LENGTH) return "模型名称过长"

        val uri = try {
            URI(normalizedUrl)
        } catch (_: Exception) {
            return "API 地址格式无效"
        }
        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = normalizeHost(uri.host.orEmpty())
        if (scheme !in setOf("http", "https") || host.isBlank()) {
            return "API 地址必须是完整的 HTTP 或 HTTPS 地址"
        }
        if (uri.userInfo != null) {
            return "API 地址不能包含用户名或密码"
        }
        if (uri.port !in -1..65_535) {
            return "API 地址端口无效"
        }
        if (uri.query != null || uri.fragment != null) {
            return "API 地址不能包含查询参数或片段"
        }
        if (scheme == "http" && !isLocalNetworkHost(host)) {
            return "远程 API 地址必须使用 HTTPS；HTTP 仅支持本机或局域网服务"
        }
        if (scheme == "http" && apiKey.isNotBlank() && !isCredentialSafeHttpHost(host)) {
            return "为保护 API Key，局域网 HTTP 地址不能携带密钥；请改用 HTTPS 或清空密钥"
        }
        if (!isLocalNetworkHost(host) && apiKey.trim().isBlank()) {
            return "远程 API 服务需要填写密钥"
        }
        return null
    }

    fun requireValid(baseUrl: String, apiKey: String, model: String) {
        validationError(baseUrl, apiKey, model)?.let { throw IllegalArgumentException(it) }
    }

    fun normalizeBaseUrl(baseUrl: String): String {
        val withoutTrailingSlash = baseUrl.trim().trimEnd('/')
        return withoutTrailingSlash.replace(Regex("/v1$", RegexOption.IGNORE_CASE), "")
    }

    fun isLocalEndpoint(baseUrl: String): Boolean {
        val host = runCatching { URI(baseUrl.trim()).host.orEmpty() }.getOrDefault("")
        return isLocalNetworkHost(normalizeHost(host))
    }

    private fun isLocalNetworkHost(host: String): Boolean {
        if (host.isBlank()) return false
        if (isCredentialSafeHttpHost(host)) return true
        if (host.endsWith(".local") || (!host.contains('.') && !host.contains(':'))) return true
        if (host.startsWith("10.") || host.startsWith("192.168.")) return true
        if (host.startsWith("172.")) {
            val secondOctet = host.substringAfter("172.").substringBefore('.').toIntOrNull()
            if (secondOctet != null && secondOctet in 16..31) return true
        }
        return host.startsWith("fc", ignoreCase = true) ||
            host.startsWith("fd", ignoreCase = true) ||
            Regex("^fe[89ab]", RegexOption.IGNORE_CASE).containsMatchIn(host)
    }

    private fun isCredentialSafeHttpHost(host: String): Boolean {
        return host == "localhost" ||
            host == "::1" ||
            host.startsWith("127.") ||
            host == "10.0.2.2" ||
            host == "10.0.3.2"
    }

    private fun normalizeHost(host: String): String =
        host.trim().removePrefix("[").removeSuffix("]").lowercase()
}
