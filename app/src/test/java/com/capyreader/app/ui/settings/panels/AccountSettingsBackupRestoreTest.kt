package com.capyreader.app.ui.settings.panels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.capyreader.app.integrations.webdav.WebDavBackupScheduler
import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.preferences.InMemorySecretStore
import com.capyreader.app.transfers.AutomaticBackupScheduler
import com.capyreader.app.transfers.BackupRestoreMode
import com.capyreader.app.transfers.BackupRestorePreview
import com.capyreader.app.transfers.BackupRestoreWorker
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
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.junit.Assert.assertTrue
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
    private val workManager = mockk<WorkManager>(relaxed = true)
    private val restoreWork = MutableStateFlow<List<WorkInfo>>(emptyList())
    private lateinit var account: Account
    private lateinit var appPreferences: AppPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkObject(WorkManager.Companion)
        every { WorkManager.getInstance(any()) } returns workManager
        every { workManager.getWorkInfosForUniqueWorkFlow(BackupRestoreWorker.WORK_NAME) } returns restoreWork
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
            every { id } returns "backup-account"
            every { source } returns Source.LOCAL
            every { preferences } returns accountPreferences
        }
    }

    @After
    fun tearDown() {
        unmockkObject(WorkManager.Companion)
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
        val request = slot<OneTimeWorkRequest>()
        verify(exactly = 1) {
            workManager.enqueueUniqueWork(BackupRestoreWorker.WORK_NAME, ExistingWorkPolicy.KEEP, capture(request))
        }
        assertEquals(uri.toString(), request.captured.workSpec.input.getString(BackupRestoreWorker.URI_KEY))
        assertEquals(account.id, request.captured.workSpec.input.getString(BackupRestoreWorker.ACCOUNT_ID_KEY))
        assertEquals(BackupRestoreMode.MERGE.name, request.captured.workSpec.input.getString(BackupRestoreWorker.MODE_KEY))
        coVerify(exactly = 0) { backupFile.restore(any(), any(), any()) }
    }

    @Test
    fun `leaving settings keeps the restore running and reopening observes it`() = runTest {
        val uri = Uri.parse("content://backup/selected.json")
        coEvery { backupFile.restorePreview(account, uri) } returns preview()
        val viewModel = buildViewModel()
        val store = ViewModelStore().also { it.put("settings", viewModel) }
        viewModel.prepareBackupImport(uri)
        advanceUntilIdle()
        viewModel.confirmBackupImport(BackupRestoreMode.REPLACE)
        restoreWork.value = listOf(mockk { every { state } returns WorkInfo.State.RUNNING })
        advanceUntilIdle()
        assertTrue(viewModel.backupImportInProgress)

        store.clear()
        val reopened = buildViewModel()
        advanceUntilIdle()

        assertTrue(reopened.backupImportInProgress)
        verify(exactly = 0) { workManager.cancelUniqueWork(any()) }
        restoreWork.value = listOf(mockk { every { state } returns WorkInfo.State.SUCCEEDED })
        advanceUntilIdle()
        assertFalse(reopened.backupImportInProgress)
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
