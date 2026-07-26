package com.capyreader.app.preferences

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

internal class AndroidKeystoreSecretStore(
    context: Context,
    preferencesName: String = APP_SECRET_PREFERENCES_NAME,
    keyAlias: String = APP_SECRET_KEY_ALIAS,
) : SecretStore {
    private val keyProvider = AndroidKeystoreKeyProvider(keyAlias)
    private val encryptedStore = EncryptedSecretStore(
        sharedPreferences = context.applicationContext.getSharedPreferences(
            preferencesName,
            Context.MODE_PRIVATE,
        ),
        cipher = AesGcmSecretCipher(keyProvider::getOrCreate),
    )

    override fun get(key: String): String? {
        return encryptedStore.get(key)
    }

    override fun set(key: String, value: String): Boolean {
        return encryptedStore.set(key, value)
    }

    override fun delete(key: String): Boolean {
        return encryptedStore.delete(key)
    }

    override fun clear(): Boolean {
        val preferencesCleared = encryptedStore.clear()
        val keyCleared = keyProvider.delete()

        return preferencesCleared && keyCleared
    }
}

internal const val APP_SECRET_PREFERENCES_NAME = "capy_secrets"
internal const val ACCOUNT_SECRET_PREFERENCES_NAME = "capy_account_secrets"
internal const val ACCOUNT_SECRET_KEY_ALIAS = "com.capyreader.app.account_secrets.v1"
private const val APP_SECRET_KEY_ALIAS = "com.capyreader.app.secrets.v1"

private class AndroidKeystoreKeyProvider(
    private val keyAlias: String,
) {
    @Synchronized
    fun getOrCreate(): SecretKey {
        val keyStore = loadKeyStore()
        val existingKey = keyStore.getKey(keyAlias, null) as? SecretKey
        if (existingKey != null) {
            return existingKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setRandomizedEncryptionRequired(true)
                .build()
        )

        return keyGenerator.generateKey()
    }

    @Synchronized
    fun delete(): Boolean {
        return try {
            val keyStore = loadKeyStore()
            if (keyStore.containsAlias(keyAlias)) {
                keyStore.deleteEntry(keyAlias)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun loadKeyStore(): KeyStore {
        return KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_SIZE_BITS = 256
    }
}
