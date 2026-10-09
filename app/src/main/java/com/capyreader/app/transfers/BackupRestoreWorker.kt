package com.capyreader.app.transfers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.capyreader.app.R
import com.capyreader.app.common.notificationManager
import com.capyreader.app.common.toast
import com.capyreader.app.preferences.AppPreferences
import com.jocmp.capy.Account
import com.jocmp.capy.logging.CapyLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BackupRestoreWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters), KoinComponent {
    private val account by inject<Account>()
    private val appPreferences by inject<AppPreferences>()
    private val backupFile by inject<CapyBackupFile>()

    override suspend fun doWork(): Result {
        val uri = inputData.getString(URI_KEY)?.let(Uri::parse) ?: return Result.failure()
        val accountID = inputData.getString(ACCOUNT_ID_KEY) ?: return Result.failure()
        val mode = BackupRestoreMode.entries.find { it.name == inputData.getString(MODE_KEY) }
            ?: return Result.failure()

        // A queued restore must never run against a newly selected account.
        if (appPreferences.accountID.get() != accountID) return Result.failure()

        return try {
            setForeground(foregroundInfo())
            val restored = withContext(Dispatchers.Main) {
                backupFile.restore(account, uri, mode)
            }
            if (restored) Result.success() else Result.failure()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            CapyLog.error("backup_restore_worker", error)
            withContext(Dispatchers.Main) {
                applicationContext.toast(R.string.backup_importer_failure)
            }
            Result.failure()
        }
    }

    private fun foregroundInfo(): ForegroundInfo {
        val title = applicationContext.getString(R.string.backup_import_button_text_in_progress)
        applicationContext.notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.backup_import_button_text),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        val cancelIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(R.drawable.ic_rounded_sync)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .addAction(
                R.drawable.ic_rounded_close,
                applicationContext.getString(R.string.opml_import_notification_cancel),
                cancelIntent,
            )
            .build()

        return ForegroundInfo(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    companion object {
        const val WORK_NAME = "backup-restore"
        internal const val URI_KEY = "backup_uri"
        internal const val ACCOUNT_ID_KEY = "account_id"
        internal const val MODE_KEY = "restore_mode"
        private const val CHANNEL_ID = "backup_restore"
        private const val NOTIFICATION_ID = 6_170_001

        fun enqueue(context: Context, accountID: String, uri: Uri, mode: BackupRestoreMode) {
            val request = OneTimeWorkRequestBuilder<BackupRestoreWorker>()
                .setInputData(
                    workDataOf(
                        URI_KEY to uri.toString(),
                        ACCOUNT_ID_KEY to accountID,
                        MODE_KEY to mode.name,
                    )
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
