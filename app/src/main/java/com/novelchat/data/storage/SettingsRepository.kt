package com.novelchat.data.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.novelchat.data.remote.AiCallStage
import com.novelchat.data.remote.model.ApiProfile
import com.novelchat.data.remote.model.ApiStageRoutes
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
    private val stageRoutesCodec = ApiStageRoutesCodec()

    companion object {
        const val DEFAULT_API_BASE_URL = "https://api.deepseek.com"
        const val DEFAULT_MODEL = "deepseek-v4-flash"

        private val API_KEY = stringPreferencesKey("api_key")
        private val API_BASE_URL = stringPreferencesKey("api_base_url")
        private val MODEL = stringPreferencesKey("model")
        private val API_PROFILES = stringPreferencesKey("api_profiles")
        private val CURRENT_PROFILE = stringPreferencesKey("current_profile")
        private val API_STAGE_ROUTES = stringPreferencesKey("api_stage_routes")
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
            val routes = stageRoutesCodec.decode(preferences[API_STAGE_ROUTES].orEmpty())
                .withoutProfile(name)
                .sanitize(profiles.map(ApiProfile::name).toSet())
            saveStageRoutes(preferences, routes)
            if (preferences[CURRENT_PROFILE] == name) {
                preferences.remove(CURRENT_PROFILE)
            }
        }
    }

    suspend fun getCurrentProfileName(): String = withContext(Dispatchers.IO) {
        context.dataStore.data.first()[CURRENT_PROFILE] ?: ""
    }

    suspend fun getApiStageRoutes(): ApiStageRoutes = withContext(Dispatchers.IO) {
        val preferences = context.dataStore.data.first()
        val profiles = profileCodec.decode(preferences[API_PROFILES].orEmpty())
        stageRoutesCodec.decode(preferences[API_STAGE_ROUTES].orEmpty())
            .sanitize(profiles.map(ApiProfile::name).toSet())
    }

    suspend fun saveApiStageRoute(
        stage: AiCallStage,
        profileName: String
    ): Unit = withContext(Dispatchers.IO) {
        context.dataStore.edit { preferences ->
            val profiles = profileCodec.decode(preferences[API_PROFILES].orEmpty())
            val normalizedName = profileName.trim()
            require(normalizedName.isBlank() || profiles.any { it.name == normalizedName }) {
                "指定的 API 配置不存在"
            }
            val routes = stageRoutesCodec.decode(preferences[API_STAGE_ROUTES].orEmpty())
                .assign(stage, normalizedName)
                .sanitize(profiles.map(ApiProfile::name).toSet())
            saveStageRoutes(preferences, routes)
        }
    }

    suspend fun getApiConnection(stage: AiCallStage? = null): ApiProfile =
        withContext(Dispatchers.IO) {
            val preferences = context.dataStore.data.first()
            val profiles = profileCodec.decode(preferences[API_PROFILES].orEmpty())
            val routes = stageRoutesCodec.decode(preferences[API_STAGE_ROUTES].orEmpty())
                .sanitize(profiles.map(ApiProfile::name).toSet())
            val defaultConnection = ApiProfile(
                name = preferences[CURRENT_PROFILE].orEmpty(),
                baseUrl = preferences[API_BASE_URL]
                    ?.takeIf(String::isNotBlank)
                    ?: DEFAULT_API_BASE_URL,
                apiKey = credentialCipher.decryptStored(preferences[API_KEY].orEmpty()),
                model = preferences[MODEL]
                    ?.takeIf(String::isNotBlank)
                    ?: DEFAULT_MODEL
            )
            ApiConnectionRoutingPolicy.resolve(
                defaultConnection = defaultConnection,
                profiles = profiles,
                routes = routes,
                stage = stage
            )
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

    private fun saveStageRoutes(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        routes: ApiStageRoutes
    ) {
        if (routes.routes.isEmpty()) {
            preferences.remove(API_STAGE_ROUTES)
        } else {
            preferences[API_STAGE_ROUTES] = stageRoutesCodec.encode(routes)
        }
    }
}
