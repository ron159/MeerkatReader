package com.capyreader.app.transfers

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.work.ListenableWorker.Result
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.capyreader.app.preferences.AppPreferences
import com.jocmp.capy.Account
import com.jocmp.capy.preferences.Preference
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BackupRestoreWorkerTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()
    private val testDispatcher = StandardTestDispatcher()
    private val accountID = "backup-account"
    private val uri = Uri.parse("content://backup/selected.json")
    private val account = mockk<Account>()
    private val accountIDPreference = mockk<Preference<String>>()
    private val appPreferences = mockk<AppPreferences>()
    private val backupFile = mockk<CapyBackupFile>()
    private val workManager = mockk<WorkManager>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { account.id } returns accountID
        every { accountIDPreference.get() } returns accountID
        every { appPreferences.accountID } returns accountIDPreference
        startKoin {
            modules(module {
                single { account }
                single { appPreferences }
                single { backupFile }
            })
        }

        mockkObject(WorkManager.Companion)
        every { WorkManager.getInstance(any()) } returns workManager
        val cancelIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent("cancel-backup-restore"),
            PendingIntent.FLAG_IMMUTABLE,
        )
        every { workManager.createCancelPendingIntent(any()) } returns cancelIntent
    }

    @After
    fun tearDown() {
        unmockkObject(WorkManager.Companion)
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun `matching account completes a successful restore`() = runTest {
        coEvery { backupFile.restore(account, uri, BackupRestoreMode.MERGE) } returns true
        val worker = buildWorker(BackupRestoreMode.MERGE)

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 1) { backupFile.restore(account, uri, BackupRestoreMode.MERGE) }
    }

    @Test
    fun `unsuccessful restore reports failure`() = runTest {
        coEvery { backupFile.restore(account, uri, BackupRestoreMode.REPLACE) } returns false
        val worker = buildWorker()

        val result = worker.doWork()

        assertEquals(Result.failure(), result)
        coVerify(exactly = 1) { backupFile.restore(account, uri, BackupRestoreMode.REPLACE) }
    }

    @Test
    fun `changed account prevents the queued restore`() = runTest {
        val worker = buildWorker()
        every { accountIDPreference.get() } returns "different-account"

        val result = worker.doWork()

        assertEquals(Result.failure(), result)
        coVerify(exactly = 0) { backupFile.restore(any(), any(), any()) }
    }

    @Test
    fun `restore cancellation propagates to WorkManager`() = runTest {
        val cancellation = CancellationException("Restore cancelled")
        coEvery { backupFile.restore(account, uri, BackupRestoreMode.REPLACE) } throws cancellation
        val worker = buildWorker()

        val error = runCatching { worker.doWork() }.exceptionOrNull()

        assertTrue(error is CancellationException)
        assertEquals(cancellation.message, error?.message)
        coVerify(exactly = 1) { backupFile.restore(account, uri, BackupRestoreMode.REPLACE) }
    }

    private fun buildWorker(mode: BackupRestoreMode = BackupRestoreMode.REPLACE): BackupRestoreWorker {
        return TestListenableWorkerBuilder<BackupRestoreWorker>(context)
            .setInputData(
                workDataOf(
                    BackupRestoreWorker.URI_KEY to uri.toString(),
                    BackupRestoreWorker.ACCOUNT_ID_KEY to accountID,
                    BackupRestoreWorker.MODE_KEY to mode.name,
                )
            )
            .build()
    }
}
