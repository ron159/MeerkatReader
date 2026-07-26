package com.capyreader.app.preferences

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class SecretPreferenceTest {
    private val sharedPreferences
        get() = RuntimeEnvironment.getApplication()
            .getSharedPreferences("secret_preference_test", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        sharedPreferences.edit().clear().commit()
    }

    @Test
    fun `first read migrates legacy plaintext then deletes it`() {
        val secretStore = InMemorySecretStore()
        val legacyPreference = legacyPreference().also {
            it.set("legacy-secret")
        }
        val preference = SecretPreference(
            key = "ai_api_key",
            secretStore = secretStore,
            legacyPreference = legacyPreference,
        )

        assertEquals("legacy-secret", preference.get())
        assertEquals("legacy-secret", secretStore.get("ai_api_key"))
        assertFalse(legacyPreference.isSet())
    }

    @Test
    fun `failed migration leaves legacy plaintext recoverable`() {
        val secretStore = InMemorySecretStore(failWrites = true)
        val legacyPreference = legacyPreference().also {
            it.set("legacy-secret")
        }
        val preference = SecretPreference(
            key = "ai_api_key",
            secretStore = secretStore,
            legacyPreference = legacyPreference,
        )

        assertEquals("legacy-secret", preference.get())
        assertTrue(legacyPreference.isSet())
        assertEquals("legacy-secret", legacyPreference.get())
    }

    @Test
    fun `set and delete never retain a legacy plaintext value`() {
        val secretStore = InMemorySecretStore()
        val legacyPreference = legacyPreference().also {
            it.set("old-secret")
        }
        val preference = SecretPreference(
            key = "ai_api_key",
            secretStore = secretStore,
            legacyPreference = legacyPreference,
        )

        preference.set("new-secret")

        assertEquals("new-secret", preference.get())
        assertFalse(legacyPreference.isSet())

        preference.delete()

        assertFalse(preference.isSet())
        assertFalse(legacyPreference.isSet())
        assertEquals(null, secretStore.get("ai_api_key"))
    }

    private fun legacyPreference() =
        AndroidPreferenceStore(sharedPreferences).getString("ai_api_key")
}
