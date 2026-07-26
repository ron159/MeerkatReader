package com.capyreader.app.integrations.webdav

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jocmp.capy.logging.CapyLog
import kotlinx.coroutines.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WebDavBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams), KoinComponent {
    private val uploader by inject<WebDavBackupUploader>()

    override suspend fun doWork(): Result {
        return try {
            uploader.uploadNow().fold(
                onSuccess = { Result.success() },
                onFailure = { error ->
                    CapyLog.error("webdav_backup", error)
                    if (shouldRetryWebDavBackup(error, runAttemptCount)) {
                        Result.retry()
                    } else {
                        Result.failure()
                    }
                },
            )
        } catch (error: CancellationException) {
            throw error
        }
    }
}

internal const val MAX_WEB_DAV_BACKUP_ATTEMPTS = 3

internal fun shouldRetryWebDavBackup(
    error: Throwable,
    runAttemptCount: Int,
): Boolean {
    val retryable = (error as? WebDavBackupException)?.retryable ?: true
    return retryable && runAttemptCount < MAX_WEB_DAV_BACKUP_ATTEMPTS - 1
}
