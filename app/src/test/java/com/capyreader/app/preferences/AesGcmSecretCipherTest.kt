package com.capyreader.app.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Base64
import javax.crypto.spec.SecretKeySpec

class AesGcmSecretCipherTest {
    private val cipher = AesGcmSecretCipher {
        SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
    }

    @Test
    fun `round trip does not expose plaintext`() {
        val encrypted = cipher.encrypt("ai_api_key", "secret-token")

        assertFalse(encrypted.contains("secret-token"))
        assertEquals(
            "secret-token",
            cipher.decrypt("ai_api_key", encrypted),
        )
    }

    @Test
    fun `repeated encryption uses distinct IVs`() {
        val first = cipher.encrypt("ai_api_key", "same-value")
        val second = cipher.encrypt("ai_api_key", "same-value")

        assertNotEquals(first, second)
        assertNotEquals(first.split(':')[1], second.split(':')[1])
    }

    @Test
    fun `tampering fails authentication`() {
        val encrypted = cipher.encrypt("ai_api_key", "secret-token")
        val parts = encrypted.split(':').toMutableList()
        val ciphertext = Base64.getDecoder().decode(parts[2])
        ciphertext[0] = (ciphertext[0].toInt() xor 1).toByte()
        parts[2] = Base64.getEncoder().withoutPadding().encodeToString(ciphertext)
        val tampered = parts.joinToString(":")

        assertThrows(Exception::class.java) {
            cipher.decrypt("ai_api_key", tampered)
        }
    }

    @Test
    fun `ciphertext is bound to its storage key`() {
        val encrypted = cipher.encrypt("ai_api_key", "secret-token")

        assertThrows(Exception::class.java) {
            cipher.decrypt("wallabag_access_token", encrypted)
        }
    }
}
