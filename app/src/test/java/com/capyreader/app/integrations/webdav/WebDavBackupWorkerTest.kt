package com.capyreader.app.integrations.webdav

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.preferences.InMemorySecretStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class WebDavBackupWorkerTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()
    private lateinit var appPreferences: AppPreferences

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .clear()
            .commit()
        appPreferences = AppPreferences(context, InMemorySecretStore())
    }

    @Test
    fun `webdav backup requires a connected network`() {
        assertEquals(
            NetworkType.CONNECTED,
            webDavBackupConstraints().requiredNetworkType,
        )
    }

    @Test
    fun `retryable failures stop after three attempts`() {
        val error = WebDavHttpException(503)

        assertTrue(shouldRetryWebDavBackup(error, runAttemptCount = 0))
        assertTrue(shouldRetryWebDavBackup(error, runAttemptCount = 1))
        assertFalse(shouldRetryWebDavBackup(error, runAttemptCount = 2))
        assertFalse(
            shouldRetryWebDavBackup(
                WebDavAuthenticationException(),
                runAttemptCount = 0,
            )
        )
        assertTrue(
            shouldRetryWebDavBackup(
                IOException("Database busy"),
                runAttemptCount = 0,
            )
        )
    }

    @Test
    fun `disabled configuration schedules no work`() {
        val scheduler = WebDavBackupScheduler(context, appPreferences)

        scheduler.initialize()
        scheduler.enqueueNow()

        assertTrue(workInfos(WebDavBackupScheduler.PERIODIC_WORK_NAME).isEmpty())
        assertTrue(workInfos(WebDavBackupScheduler.IMMEDIATE_WORK_NAME).isEmpty())
    }

    @Test
    fun `configured option schedules daily and manual work independently`() {
        appPreferences.accountID.set("account")
        appPreferences.webDavBackupOptions.directoryUrl.set("https://dav.example/backups/")
        appPreferences.webDavBackupOptions.username.set("reader")
        appPreferences.webDavBackupOptions.password.set("app-secret")
        val scheduler = WebDavBackupScheduler(context, appPreferences)

        scheduler.setEnabled(true)
        scheduler.enqueueNow()

        assertEquals(
            WorkInfo.State.ENQUEUED,
            workInfos(WebDavBackupScheduler.PERIODIC_WORK_NAME).single().state,
        )
        assertEquals(
            WorkInfo.State.ENQUEUED,
            workInfos(WebDavBackupScheduler.IMMEDIATE_WORK_NAME).single().state,
        )
    }

    private fun workInfos(name: String): List<WorkInfo> {
        return WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(name)
            .get()
    }
}
