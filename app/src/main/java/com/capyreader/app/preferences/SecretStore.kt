package com.capyreader.app.preferences

import android.content.SharedPreferences

internal interface SecretStore {
    fun get(key: String): String?

    fun set(key: String, value: String): Boolean

    fun delete(key: String): Boolean

    fun clear(): Boolean
}

internal interface SecretCipher {
    fun encrypt(storageKey: String, plaintext: String): String

    fun decrypt(storageKey: String, envelope: String): String
}

internal class EncryptedSecretStore(
    private val sharedPreferences: SharedPreferences,
    private val cipher: SecretCipher,
) : SecretStore {
    override fun get(key: String): String? {
        val envelope = sharedPreferences.getString(key, null) ?: return null

        return try {
            cipher.decrypt(key, envelope)
        } catch (_: Exception) {
            null
        }
    }

    override fun set(key: String, value: String): Boolean {
        return try {
            val envelope = cipher.encrypt(key, value)
            sharedPreferences.edit().putString(key, envelope).commit()
        } catch (_: Exception) {
            false
        }
    }

    override fun delete(key: String): Boolean {
        return try {
            sharedPreferences.edit().remove(key).commit()
        } catch (_: Exception) {
            false
        }
    }

    override fun clear(): Boolean {
        return try {
            sharedPreferences.edit().clear().commit()
        } catch (_: Exception) {
            false
        }
    }
}
