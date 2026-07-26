package com.capyreader.app.integrations.webdav

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.capyreader.app.preferences.AppPreferences
import java.util.concurrent.TimeUnit

class WebDavBackupScheduler(
    private val context: Context,
    private val appPreferences: AppPreferences,
) {
    private var periodicConfigured: Boolean? = null

    fun initialize() {
        updatePeriodicWork()
    }

    fun setEnabled(enabled: Boolean) {
        appPreferences.webDavBackupOptions.enabled.set(enabled)
        updatePeriodicWork()
    }

    fun configurationChanged() {
        updatePeriodicWork()
    }

    fun enqueueNow() {
        if (!isConfigured()) {
            return
        }

        val request = OneTimeWorkRequestBuilder<WebDavBackupWorker>()
            .setConstraints(webDavBackupConstraints())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WEB_DAV_BACKOFF_SECONDS,
                TimeUnit.SECONDS,
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun clear() {
        WorkManager.getInstance(context).run {
            cancelUniqueWork(PERIODIC_WORK_NAME)
            cancelUniqueWork(IMMEDIATE_WORK_NAME)
        }
        appPreferences.webDavBackupOptions.enabled.set(false)
        appPreferences.webDavBackupOptions.lastBackupAt.set(0L)
        appPreferences.webDavBackupOptions.lastError.set("")
        periodicConfigured = false
    }

    private fun updatePeriodicWork() {
        val workManager = WorkManager.getInstance(context)
        val configured = isConfigured()
        if (periodicConfigured == configured) {
            return
        }
        periodicConfigured = configured

        if (!configured) {
            workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
            return
        }

        val request = PeriodicWorkRequestBuilder<WebDavBackupWorker>(
            1,
            TimeUnit.DAYS,
        )
            .setConstraints(webDavBackupConstraints())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WEB_DAV_BACKOFF_SECONDS,
                TimeUnit.SECONDS,
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private fun isConfigured(): Boolean {
        return appPreferences.isLoggedIn &&
            appPreferences.webDavBackupOptions.isConfigured()
    }

    companion object {
        internal const val PERIODIC_WORK_NAME = "webdav-backup-periodic"
        internal const val IMMEDIATE_WORK_NAME = "webdav-backup-now"
        internal const val WEB_DAV_BACKOFF_SECONDS = 30L
    }
}

internal fun webDavBackupConstraints(): Constraints {
    return Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
}
