package com.novelchat.data.storage

import com.novelchat.data.remote.model.ApiProfile
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ApiProfileCredentialCodecTest {
    private val cipher = CredentialCipher {
        SecretKeySpec(ByteArray(32) { index -> (31 - index).toByte() }, "AES")
    }
    private val codec = ApiProfileCredentialCodec(cipher)

    @Test
    fun encode_encryptsEveryProfileAndDecodeRestoresPlaintext() {
        val profiles = listOf(
            ApiProfile("DeepSeek", "https://api.deepseek.com", "secret-one", "deepseek-v4-flash"),
            ApiProfile("本地", "http://192.168.1.2:1234", "", "local-model")
        )

        val stored = codec.encode(profiles)

        assertFalse(stored.contains("secret-one"))
        assertEquals(profiles, codec.decode(stored))
        assertNull(codec.migrateLegacy(stored))
    }

    @Test
    fun migrateLegacy_encryptsPlainAndMixedProfileKeys() {
        val encryptedExisting = cipher.encrypt("already-secret")
        val legacyJson = """
            [
              {"name":"旧配置","base_url":"https://example.com","api_key":"legacy-secret","model":"model-a"},
              {"name":"已加密","base_url":"https://api.deepseek.com","api_key":"$encryptedExisting","model":"deepseek-v4-flash"}
            ]
        """.trimIndent()

        val migrated = codec.migrateLegacy(legacyJson)

        assertNotNull(migrated)
        assertFalse(migrated.orEmpty().contains("legacy-secret"))
        assertFalse(migrated.orEmpty().contains("already-secret"))
        assertEquals(
            listOf("legacy-secret", "already-secret"),
            codec.decode(migrated.orEmpty()).map(ApiProfile::apiKey)
        )
    }
}
