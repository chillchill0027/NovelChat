package com.novelchat.data.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class CredentialCipher(
    private val keyProvider: () -> SecretKey = AndroidKeyStoreCredentialKey::getOrCreate
) {
    fun encrypt(value: String): String {
        if (value.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
        cipher.updateAAD(AAD)
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return listOf(
            PREFIX,
            encoder.encodeToString(cipher.iv),
            encoder.encodeToString(encrypted)
        ).joinToString(SEPARATOR)
    }

    fun decryptStored(value: String): String {
        if (!isEncrypted(value)) return value
        return try {
            decrypt(value)
        } catch (_: Exception) {
            ""
        }
    }

    fun isEncrypted(value: String): Boolean = value.startsWith("$PREFIX$SEPARATOR")

    private fun decrypt(value: String): String {
        val parts = value.split(SEPARATOR, limit = 3)
        require(parts.size == 3 && parts[0] == PREFIX) { "凭据密文格式无效" }
        val iv = decoder.decode(parts[1])
        val encrypted = decoder.decode(parts[2])
        require(iv.size in 12..16 && encrypted.size >= 16) { "凭据密文内容无效" }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(AAD)
        return String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
    }

    private companion object {
        const val PREFIX = "enc-v1"
        const val SEPARATOR = ":"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        val AAD = "NovelChat credential v1".toByteArray(StandardCharsets.UTF_8)
        val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
        val decoder: Base64.Decoder = Base64.getUrlDecoder()
    }
}

private object AndroidKeyStoreCredentialKey {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "novelchat_api_credentials_v1"

    @Volatile
    private var cachedKey: SecretKey? = null

    @Synchronized
    fun getOrCreate(): SecretKey {
        cachedKey?.let { return it }
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { existing ->
            cachedKey = existing
            return existing
        }

        val generated = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
        cachedKey = generated
        return generated
    }
}
