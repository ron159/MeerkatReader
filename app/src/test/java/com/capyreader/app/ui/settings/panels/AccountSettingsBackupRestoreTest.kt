package com.capyreader.app.ui.settings.panels

import android.app.Application
import android.net.Uri
import com.capyreader.app.integrations.webdav.WebDavBackupScheduler
import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.preferences.InMemorySecretStore
import com.capyreader.app.transfers.AutomaticBackupScheduler
import com.capyreader.app.transfers.BackupRestoreMode
import com.capyreader.app.transfers.BackupRestorePreview
import com.capyreader.app.transfers.CapyBackupFile
import com.jocmp.capy.Account
import com.jocmp.capy.AccountManager
import com.jocmp.capy.AccountPreferences
import com.jocmp.capy.accounts.Source
import com.jocmp.capy.preferences.Preference
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AccountSettingsBackupRestoreTest {
    private val testDispatcher = StandardTestDispatcher()
    private val accountManager = mockk<AccountManager>(relaxed = true)
    private val automaticBackupScheduler = mockk<AutomaticBackupScheduler>(relaxed = true)
    private val backupFile = mockk<CapyBackupFile>(relaxed = true)
    private val webDavBackupScheduler = mockk<WebDavBackupScheduler>(relaxed = true)
    private lateinit var account: Account
    private lateinit var appPreferences: AppPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        appPreferences = AppPreferences(
            RuntimeEnvironment.getApplication(),
            InMemorySecretStore(),
        ).also(AppPreferences::clearAll)

        val url = mockPreference("")
        val username = mockPreference("")
        val lastRefreshedAt = mockPreference(0L)
        val accountPreferences = mockk<AccountPreferences>()
        every { accountPreferences.url } returns url
        every { accountPreferences.username } returns username
        every { accountPreferences.lastRefreshedAt } returns lastRefreshedAt
        account = mockk {
            every { source } returns Source.LOCAL
            every { preferences } returns accountPreferences
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `preview is read only and confirmation consumes pending uri once`() = runTest {
        val uri = Uri.parse("content://backup/selected.json")
        val preview = preview()
        coEvery { backupFile.restorePreview(account, uri) } returns preview
        val viewModel = buildViewModel()

        viewModel.prepareBackupImport(uri)
        advanceUntilIdle()

        assertEquals(preview, viewModel.backupRestorePreview)
        coVerify(exactly = 0) { backupFile.restore(any(), any(), any()) }

        viewModel.confirmBackupImport(BackupRestoreMode.MERGE)
        viewModel.confirmBackupImport(BackupRestoreMode.REPLACE)
        assertNull(viewModel.backupRestorePreview)
        advanceUntilIdle()

        assertFalse(viewModel.backupImportInProgress)
        coVerify(exactly = 1) {
            backupFile.restore(account, uri, BackupRestoreMode.MERGE)
        }
    }

    @Test
    fun `cancel invalidates an in flight preview and its pending uri`() = runTest {
        val uri = Uri.parse("content://backup/slow.json")
        val pendingPreview = CompletableDeferred<BackupRestorePreview?>()
        coEvery { backupFile.restorePreview(account, uri) } coAnswers {
            pendingPreview.await()
        }
        val viewModel = buildViewModel()

        viewModel.prepareBackupImport(uri)
        runCurrent()
        viewModel.cancelBackupImport()
        pendingPreview.complete(preview())
        advanceUntilIdle()
        viewModel.confirmBackupImport(BackupRestoreMode.REPLACE)
        advanceUntilIdle()

        assertNull(viewModel.backupRestorePreview)
        coVerify(exactly = 0) { backupFile.restore(any(), any(), any()) }
    }

    private fun buildViewModel() = AccountSettingsViewModel(
        accountManager = accountManager,
        account = account,
        appPreferences = appPreferences,
        automaticBackupScheduler = automaticBackupScheduler,
        backupFile = backupFile,
        webDavBackupScheduler = webDavBackupScheduler,
        application = RuntimeEnvironment.getApplication(),
    )

    private fun preview() = BackupRestorePreview(
        version = 2,
        source = Source.LOCAL,
        hasSubscriptions = true,
        savedSearchCount = 2,
        readLaterCount = 3,
        starredCount = 4,
        hasRules = true,
        hasAiSettings = true,
    )

    private fun <T> mockPreference(value: T): Preference<T> = mockk(relaxed = true) {
        every { get() } returns value
        every { changes() } returns flowOf(value)
    }
}
