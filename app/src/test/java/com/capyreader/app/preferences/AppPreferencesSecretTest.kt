package com.capyreader.app.preferences

import android.content.Context
import androidx.preference.PreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class AppPreferencesSecretTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()
    private val defaultPreferences
        get() = PreferenceManager.getDefaultSharedPreferences(context)
    private val encryptedPreferences
        get() = context.getSharedPreferences("app_preferences_secret_test", Context.MODE_PRIVATE)
    private val cipher = AesGcmSecretCipher {
        SecretKeySpec(ByteArray(32) { (it + 2).toByte() }, "AES")
    }

    @Before
    fun setUp() {
        defaultPreferences.edit().clear().commit()
        encryptedPreferences.edit().clear().commit()
    }

    @Test
    fun `AI and Wallabag credentials persist outside ordinary preferences`() {
        val preferences = appPreferences()

        preferences.aiOptions.apiKey.set("ai-secret")
        preferences.wallabagOptions.accessToken.set("wallabag-secret")

        assertFalse(defaultPreferences.contains("ai_api_key"))
        assertFalse(defaultPreferences.contains("wallabag_access_token"))
        assertFalse(encryptedValue("ai_api_key").contains("ai-secret"))
        assertFalse(encryptedValue("wallabag_access_token").contains("wallabag-secret"))

        val restored = appPreferences()
        assertEquals("ai-secret", restored.aiOptions.apiKey.get())
        assertEquals("wallabag-secret", restored.wallabagOptions.accessToken.get())
    }

    @Test
    fun `legacy app credentials migrate independently`() {
        defaultPreferences.edit()
            .putString("ai_api_key", "legacy-ai")
            .putString("wallabag_access_token", "legacy-wallabag")
            .commit()

        val preferences = appPreferences()

        assertEquals("legacy-ai", preferences.aiOptions.apiKey.get())
        assertEquals("legacy-wallabag", preferences.wallabagOptions.accessToken.get())
        assertFalse(defaultPreferences.contains("ai_api_key"))
        assertFalse(defaultPreferences.contains("wallabag_access_token"))
        assertTrue(encryptedPreferences.contains("ai_api_key"))
        assertTrue(encryptedPreferences.contains("wallabag_access_token"))
    }

    @Test
    fun `clear all removes ordinary and encrypted settings`() {
        val preferences = appPreferences()
        preferences.aiOptions.enabled.set(true)
        preferences.aiOptions.apiKey.set("ai-secret")
        preferences.wallabagOptions.accessToken.set("wallabag-secret")

        preferences.clearAll()

        assertTrue(defaultPreferences.all.isEmpty())
        assertTrue(encryptedPreferences.all.isEmpty())
        assertEquals("", preferences.aiOptions.apiKey.get())
        assertEquals("", preferences.wallabagOptions.accessToken.get())
    }

    private fun appPreferences() =
        AppPreferences(
            context = context,
            secretStore = EncryptedSecretStore(encryptedPreferences, cipher),
        )

    private fun encryptedValue(key: String): String {
        return encryptedPreferences.getString(key, "").orEmpty()
    }
}
