package com.novelchat.data.storage

import com.google.gson.Gson
import com.novelchat.data.remote.model.ApiProfile

internal class ApiProfileCredentialCodec(
    private val credentialCipher: CredentialCipher,
    private val gson: Gson = Gson()
) {
    fun decode(json: String, decryptCredentials: Boolean = true): List<ApiProfile> {
        return try {
            gson.fromJson(json, Array<ApiProfile>::class.java)
                .orEmpty()
                .filterNotNull()
                .mapNotNull { profile ->
                    val name = profile.name.orEmpty().trim()
                    if (name.isBlank()) return@mapNotNull null
                    val storedKey = profile.apiKey.orEmpty().trim()
                    ApiProfile(
                        name = name,
                        baseUrl = profile.baseUrl.orEmpty().trim(),
                        apiKey = if (decryptCredentials) {
                            credentialCipher.decryptStored(storedKey)
                        } else {
                            storedKey
                        },
                        model = profile.model.orEmpty().trim()
                    )
                }
                .distinctBy { it.name }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun encode(profiles: List<ApiProfile>): String {
        return gson.toJson(profiles.map { profile ->
            profile.copy(
                apiKey = profile.apiKey.trim()
                    .takeIf(String::isNotEmpty)
                    ?.let(credentialCipher::encrypt)
                    .orEmpty()
            )
        })
    }

    fun migrateLegacy(json: String): String? {
        val storedProfiles = decode(json, decryptCredentials = false)
        if (storedProfiles.none { profile ->
                profile.apiKey.isNotBlank() && !credentialCipher.isEncrypted(profile.apiKey)
            }
        ) return null

        val plaintextProfiles = storedProfiles.map { profile ->
            profile.copy(apiKey = credentialCipher.decryptStored(profile.apiKey))
        }
        return encode(plaintextProfiles)
    }
}
