package com.novelchat.data.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.novelchat.data.remote.model.ApiProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

val Context.dataStore by preferencesDataStore(name = "novelchat_settings")

class SettingsRepository(private val context: Context) {

    private val credentialCipher = CredentialCipher()
    private val profileCodec = ApiProfileCredentialCodec(credentialCipher)

    companion object {
        const val DEFAULT_API_BASE_URL = "https://api.deepseek.com"
        const val DEFAULT_MODEL = "deepseek-v4-flash"

        private val API_KEY = stringPreferencesKey("api_key")
        private val API_BASE_URL = stringPreferencesKey("api_base_url")
        private val MODEL = stringPreferencesKey("model")
        private val API_PROFILES = stringPreferencesKey("api_profiles")
        private val CURRENT_PROFILE = stringPreferencesKey("current_profile")
    }

    // ========== 单配置模式（向后兼容） ==========

    val apiKey: Flow<String> = context.dataStore.data.map {
        credentialCipher.decryptStored(it[API_KEY].orEmpty())
    }.flowOn(Dispatchers.IO)
    val apiBaseUrl: Flow<String> = context.dataStore.data.map {
        it[API_BASE_URL]?.takeIf { saved -> saved.isNotBlank() } ?: DEFAULT_API_BASE_URL
    }
    val model: Flow<String> = context.dataStore.data.map {
        it[MODEL]?.takeIf { saved -> saved.isNotBlank() } ?: DEFAULT_MODEL
    }

    suspend fun saveApiConnection(
        url: String,
        key: String,
        model: String,
        currentProfileName: String = ""
    ): Unit = withContext(Dispatchers.IO) {
        val encryptedKey = credentialCipher.encrypt(key)
        context.dataStore.edit { preferences ->
            preferences[API_BASE_URL] = url
            preferences[API_KEY] = encryptedKey
            preferences[MODEL] = model
            if (currentProfileName.isBlank()) {
                preferences.remove(CURRENT_PROFILE)
            } else {
                preferences[CURRENT_PROFILE] = currentProfileName
            }
        }
    }

    // ========== 多配置支持 ==========

    suspend fun getApiProfiles(): List<ApiProfile> = withContext(Dispatchers.IO) {
        val json = context.dataStore.data.first()[API_PROFILES]
            ?: return@withContext emptyList()
        profileCodec.decode(json)
    }

    suspend fun saveApiProfile(name: String, url: String, key: String, model: String): Unit =
        withContext(Dispatchers.IO) {
        val encryptedKey = credentialCipher.encrypt(key)
        context.dataStore.edit { preferences ->
            val profiles = profileCodec.decode(preferences[API_PROFILES].orEmpty()).toMutableList()
            val existing = profiles.indexOfFirst { it.name == name }
            val profile = ApiProfile(name = name, baseUrl = url, apiKey = key, model = model)
            if (existing >= 0) profiles[existing] = profile else profiles.add(profile)

            preferences[API_PROFILES] = profileCodec.encode(profiles)
            preferences[API_BASE_URL] = url
            preferences[API_KEY] = encryptedKey
            preferences[MODEL] = model
            preferences[CURRENT_PROFILE] = name
        }
    }

    suspend fun deleteApiProfile(name: String): Unit = withContext(Dispatchers.IO) {
        context.dataStore.edit { preferences ->
            val profiles = profileCodec.decode(preferences[API_PROFILES].orEmpty()).toMutableList()
            profiles.removeAll { it.name == name }
            preferences[API_PROFILES] = profileCodec.encode(profiles)
            if (preferences[CURRENT_PROFILE] == name) {
                preferences.remove(CURRENT_PROFILE)
            }
        }
    }

    suspend fun getCurrentProfileName(): String = withContext(Dispatchers.IO) {
        context.dataStore.data.first()[CURRENT_PROFILE] ?: ""
    }

    suspend fun migrateLegacyCredentials(): Unit = withContext(Dispatchers.IO) {
        context.dataStore.edit { preferences ->
            preferences[API_KEY]
                ?.takeIf { it.isNotBlank() && !credentialCipher.isEncrypted(it) }
                ?.let { legacyKey -> preferences[API_KEY] = credentialCipher.encrypt(legacyKey) }

            preferences[API_PROFILES]?.let { rawProfiles ->
                profileCodec.migrateLegacy(rawProfiles)?.let { encryptedProfiles ->
                    preferences[API_PROFILES] = encryptedProfiles
                }
            }
        }
    }
}
