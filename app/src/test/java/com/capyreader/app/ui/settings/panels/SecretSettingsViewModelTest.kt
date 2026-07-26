package com.capyreader.app.ui.settings.panels

import android.content.Context
import androidx.preference.PreferenceManager
import com.capyreader.app.ai.ArticleAiRepository
import com.capyreader.app.integrations.webdav.WebDavBackupClient
import com.capyreader.app.integrations.webdav.WebDavBackupScheduler
import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.preferences.InMemorySecretStore
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class SecretSettingsViewModelTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()
    private lateinit var secretStore: InMemorySecretStore
    private lateinit var preferences: AppPreferences

    @Before
    fun setUp() {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .clear()
            .commit()
        secretStore = InMemorySecretStore()
        preferences = AppPreferences(context, secretStore)
    }

    @Test
    fun `AI settings update encrypted preference facade`() {
        val viewModel = AiSettingsViewModel(
            appPreferences = preferences,
            articleAiRepository = mockk<ArticleAiRepository>(relaxed = true),
        )

        viewModel.updateApiKey("updated-ai-key")

        assertEquals("updated-ai-key", viewModel.apiKey)
        assertEquals("updated-ai-key", secretStore.get("ai_api_key"))
        assertFalse(
            PreferenceManager.getDefaultSharedPreferences(context)
                .contains("ai_api_key")
        )
    }

    @Test
    fun `integration settings update encrypted preference facade`() {
        val viewModel = integrationViewModel()

        viewModel.updateWallabagAccessToken("updated-wallabag-token")

        assertEquals("updated-wallabag-token", viewModel.wallabagAccessToken)
        assertEquals(
            "updated-wallabag-token",
            secretStore.get("wallabag_access_token"),
        )
        assertFalse(
            PreferenceManager.getDefaultSharedPreferences(context)
                .contains("wallabag_access_token")
        )
    }

    @Test
    fun `WebDAV settings update encrypted password facade`() {
        val viewModel = integrationViewModel()

        viewModel.updateWebDavPassword("updated-webdav-password")

        assertEquals("updated-webdav-password", viewModel.webDavPassword)
        assertEquals(
            "updated-webdav-password",
            secretStore.get("webdav_backup_password"),
        )
        assertFalse(
            PreferenceManager.getDefaultSharedPreferences(context)
                .contains("webdav_backup_password")
        )
    }

    @Test
    fun `WebDAV URL credentials are removed before persistence`() {
        val viewModel = integrationViewModel()

        viewModel.updateWebDavDirectoryUrl(
            "https://embedded:secret@dav.example/backups/"
        )

        assertEquals(
            "https://dav.example/backups/",
            viewModel.webDavDirectoryUrl,
        )
        assertFalse(
            PreferenceManager.getDefaultSharedPreferences(context)
                .getString("webdav_backup_directory_url", "")
                .orEmpty()
                .contains("secret")
        )
    }

    @Test
    fun `failed encrypted writes remain a missing credential state`() {
        secretStore.failWrites = true
        val aiViewModel = AiSettingsViewModel(
            appPreferences = preferences,
            articleAiRepository = mockk<ArticleAiRepository>(relaxed = true),
        )
        val integrationViewModel = integrationViewModel()

        aiViewModel.updateApiKey("unsaved-ai-key")
        integrationViewModel.updateWallabagAccessToken("unsaved-wallabag-token")
        integrationViewModel.updateWebDavPassword("unsaved-webdav-password")

        assertEquals("", aiViewModel.apiKey)
        assertEquals("", integrationViewModel.wallabagAccessToken)
        assertEquals("", integrationViewModel.webDavPassword)
        assertFalse(
            PreferenceManager.getDefaultSharedPreferences(context)
                .contains("ai_api_key")
        )
        assertFalse(
            PreferenceManager.getDefaultSharedPreferences(context)
                .contains("wallabag_access_token")
        )
        assertFalse(
            PreferenceManager.getDefaultSharedPreferences(context)
                .contains("webdav_backup_password")
        )
    }

    private fun integrationViewModel(): IntegrationSettingsViewModel {
        return IntegrationSettingsViewModel(
            appPreferences = preferences,
            webDavBackupClient = mockk<WebDavBackupClient>(relaxed = true),
            webDavBackupScheduler = mockk<WebDavBackupScheduler>(relaxed = true),
        )
    }
}
