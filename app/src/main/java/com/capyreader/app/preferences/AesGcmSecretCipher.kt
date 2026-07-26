package com.capyreader.app.preferences

import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class AesGcmSecretCipher(
    private val secretKey: () -> SecretKey,
) : SecretCipher {
    override fun encrypt(storageKey: String, plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        cipher.updateAAD(storageKey.toByteArray(StandardCharsets.UTF_8))

        val iv = encoder.encodeToString(cipher.iv)
        val ciphertext = encoder.encodeToString(
            cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        )

        return "$ENVELOPE_VERSION:$iv:$ciphertext"
    }

    override fun decrypt(storageKey: String, envelope: String): String {
        val parts = envelope.split(':', limit = 3)
        require(parts.size == 3 && parts[0] == ENVELOPE_VERSION)

        val iv = decoder.decode(parts[1])
        require(iv.size == GCM_IV_BYTES)
        val ciphertext = decoder.decode(parts[2])
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(GCM_TAG_BITS, iv),
        )
        cipher.updateAAD(storageKey.toByteArray(StandardCharsets.UTF_8))

        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val ENVELOPE_VERSION = "v1"
        const val GCM_TAG_BITS = 128
        const val GCM_IV_BYTES = 12

        val encoder: Base64.Encoder = Base64.getEncoder().withoutPadding()
        val decoder: Base64.Decoder = Base64.getDecoder()
    }
}
