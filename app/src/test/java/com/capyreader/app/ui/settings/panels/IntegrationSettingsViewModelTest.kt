package com.capyreader.app.ui.settings.panels

import android.app.Application
import androidx.preference.PreferenceManager
import com.capyreader.app.integrations.webdav.WebDavAuthenticationException
import com.capyreader.app.integrations.webdav.WebDavBackupClient
import com.capyreader.app.integrations.webdav.WebDavBackupScheduler
import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.preferences.InMemorySecretStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class IntegrationSettingsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val webDavBackupClient = mockk<WebDavBackupClient>()
    private val webDavBackupScheduler = mockk<WebDavBackupScheduler>(relaxed = true)
    private lateinit var appPreferences: AppPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context: Application = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .clear()
            .commit()
        appPreferences = AppPreferences(
            context,
            InMemorySecretStore(),
        ).also(AppPreferences::clearAll)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `configuration edit strips URL credentials and cancels in flight test`() = runTest {
        val pendingResult = CompletableDeferred<Result<Unit>>()
        coEvery { webDavBackupClient.testConnection() } coAnswers {
            pendingResult.await()
        }
        appPreferences.webDavBackupOptions.lastError.set("Stale failure")
        val viewModel = buildViewModel()

        viewModel.testWebDavConnection()
        runCurrent()
        assertEquals(
            WebDavConnectionTestState.TESTING,
            viewModel.webDavConnectionTestState,
        )

        viewModel.updateWebDavDirectoryUrl(
            "https://embedded:secret@dav.example/backups/"
        )
        pendingResult.complete(Result.failure(WebDavAuthenticationException()))
        advanceUntilIdle()

        assertEquals(
            "https://dav.example/backups/",
            viewModel.webDavDirectoryUrl,
        )
        assertEquals("", appPreferences.webDavBackupOptions.lastError.get())
        assertEquals(
            WebDavConnectionTestState.IDLE,
            viewModel.webDavConnectionTestState,
        )
        assertEquals("", viewModel.webDavConnectionTestError)
        coVerify(exactly = 1) { webDavBackupClient.testConnection() }
        verify(exactly = 1) { webDavBackupScheduler.configurationChanged() }
    }

    @Test
    fun `connection result exposes stable success and failure states`() = runTest {
        coEvery {
            webDavBackupClient.testConnection()
        } returns Result.success(Unit)
        val viewModel = buildViewModel()

        viewModel.testWebDavConnection()
        advanceUntilIdle()
        assertEquals(
            WebDavConnectionTestState.SUCCESS,
            viewModel.webDavConnectionTestState,
        )
        assertEquals("", viewModel.webDavConnectionTestError)

        coEvery {
            webDavBackupClient.testConnection()
        } returns Result.failure(WebDavAuthenticationException())
        viewModel.testWebDavConnection()
        advanceUntilIdle()

        assertEquals(
            WebDavConnectionTestState.FAILED,
            viewModel.webDavConnectionTestState,
        )
        assertEquals(
            "WebDAV authentication failed",
            viewModel.webDavConnectionTestError,
        )
        assertFalse(viewModel.webDavConnectionTestError.contains("app-secret"))
        coVerify(exactly = 2) { webDavBackupClient.testConnection() }
    }

    @Test
    fun `scheduler actions follow explicit setting changes exactly once`() {
        val viewModel = buildViewModel()

        viewModel.updateWebDavBackupEnabled(true)
        viewModel.updateWebDavUsername("reader")
        viewModel.updateWebDavPassword("app-password")
        viewModel.backUpToWebDavNow()

        assertEquals(true, viewModel.webDavBackupEnabled)
        assertEquals("reader", viewModel.webDavUsername)
        assertEquals("app-password", viewModel.webDavPassword)
        verify(exactly = 1) { webDavBackupScheduler.setEnabled(true) }
        verify(exactly = 2) { webDavBackupScheduler.configurationChanged() }
        verify(exactly = 1) { webDavBackupScheduler.enqueueNow() }
    }

    @Test
    fun `Wallabag credential edits clear stale errors`() {
        appPreferences.wallabagOptions.lastError.set("Expired token")
        val serverViewModel = buildViewModel()

        serverViewModel.updateWallabagServerUrl("https://wallabag.example")

        assertEquals("", serverViewModel.wallabagLastError)
        assertEquals("", appPreferences.wallabagOptions.lastError.get())

        appPreferences.wallabagOptions.lastError.set("Expired again")
        val tokenViewModel = buildViewModel()
        tokenViewModel.updateWallabagAccessToken("replacement-token")

        assertEquals("", tokenViewModel.wallabagLastError)
        assertEquals("", appPreferences.wallabagOptions.lastError.get())
    }

    private fun buildViewModel() = IntegrationSettingsViewModel(
        appPreferences = appPreferences,
        webDavBackupClient = webDavBackupClient,
        webDavBackupScheduler = webDavBackupScheduler,
    )
}
