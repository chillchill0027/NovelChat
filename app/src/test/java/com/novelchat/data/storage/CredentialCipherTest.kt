package com.novelchat.data.storage

import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CredentialCipherTest {
    private val key = SecretKeySpec(ByteArray(32) { index -> (index + 1).toByte() }, "AES")
    private val cipher = CredentialCipher { key }

    @Test
    fun encrypt_roundTripsWithRandomizedCiphertext() {
        val secret = "sk-" + "test-value-123456"
        val first = cipher.encrypt(secret)
        val second = cipher.encrypt(secret)

        assertNotEquals(secret, first)
        assertNotEquals(first, second)
        assertFalse(first.contains(secret))
        assertEquals(secret, cipher.decryptStored(first))
        assertEquals(secret, cipher.decryptStored(second))
    }

    @Test
    fun decryptStored_supportsLegacyAndRejectsTampering() {
        assertEquals("legacy-key", cipher.decryptStored("legacy-key"))
        assertEquals("", cipher.decryptStored(""))

        val encrypted = cipher.encrypt("private-key")
        val replacement = if (encrypted.last() == 'A') 'B' else 'A'
        val tampered = encrypted.dropLast(1) + replacement
        assertEquals("", cipher.decryptStored(tampered))
    }
}
