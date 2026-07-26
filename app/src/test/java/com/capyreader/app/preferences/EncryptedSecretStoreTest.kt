package com.capyreader.app.preferences

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class EncryptedSecretStoreTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()
    private val sharedPreferences
        get() = context.getSharedPreferences("encrypted_secret_store_test", Context.MODE_PRIVATE)
    private val cipher = AesGcmSecretCipher {
        SecretKeySpec(ByteArray(32) { (it + 1).toByte() }, "AES")
    }

    @Before
    fun setUp() {
        sharedPreferences.edit().clear().commit()
    }

    @Test
    fun `set persists ciphertext and get decrypts it`() {
        val store = EncryptedSecretStore(sharedPreferences, cipher)

        assertTrue(store.set("ai_api_key", "secret-token"))

        assertEquals("secret-token", store.get("ai_api_key"))
        assertFalse(
            sharedPreferences.getString("ai_api_key", "").orEmpty()
                .contains("secret-token")
        )
    }

    @Test
    fun `corrupt ciphertext becomes a missing credential`() {
        val store = EncryptedSecretStore(sharedPreferences, cipher)
        sharedPreferences.edit()
            .putString("ai_api_key", "v1:not-base64:not-base64")
            .commit()

        assertNull(store.get("ai_api_key"))
    }

    @Test
    fun `delete and clear remove encrypted values`() {
        val store = EncryptedSecretStore(sharedPreferences, cipher)
        store.set("ai_api_key", "ai-secret")
        store.set("wallabag_access_token", "wallabag-secret")

        assertTrue(store.delete("ai_api_key"))
        assertNull(store.get("ai_api_key"))
        assertEquals("wallabag-secret", store.get("wallabag_access_token"))

        assertTrue(store.clear())
        assertNull(store.get("wallabag_access_token"))
        assertTrue(sharedPreferences.all.isEmpty())
    }
}
