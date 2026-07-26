package com.capyreader.app.ai

import android.content.Context
import androidx.preference.PreferenceManager
import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.preferences.InMemorySecretStore
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
class AiBackgroundPreviewPreferencesTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()
    private lateinit var preferences: AppPreferences

    @Before
    fun setUp() {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .clear()
            .commit()
        preferences = AppPreferences(context, InMemorySecretStore()).also {
            it.aiOptions.enabled.set(true)
            it.aiOptions.backgroundPreviewsEnabled.set(true)
        }
    }

    @Test
    fun `background worker gate reads encrypted credential facade`() {
        assertFalse(preferences.aiOptions.canRunBackgroundPreviews())

        preferences.aiOptions.apiKey.set("secret-token")

        assertTrue(preferences.aiOptions.canRunBackgroundPreviews())
    }
}
