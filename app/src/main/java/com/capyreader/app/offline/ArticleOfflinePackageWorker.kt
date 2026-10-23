package com.capyreader.app.offline

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jocmp.capy.logging.CapyLog
import kotlinx.coroutines.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class ArticleOfflinePackageWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams), KoinComponent {
    private val downloader by inject<ArticleOfflinePackageDownloader>()

    override suspend fun doWork(): Result {
        return try {
            downloader.queueAlwaysKeepOffline()
            // Process the finite pending snapshot once. Successful batches must
            // not consume the WorkManager attempt budget intended for failures.
            val result = downloader.downloadQueued(
                limit = Int.MAX_VALUE,
                retryFailed = runAttemptCount < MAX_DOWNLOAD_ATTEMPTS,
            )
            downloader.cleanup()
            when {
                result.hasMore -> Result.retry()
                result.failed > 0 && runAttemptCount < MAX_DOWNLOAD_ATTEMPTS - 1 -> Result.retry()
                result.failed > 0 -> Result.failure()
                else -> Result.success()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CapyLog.error("article_offline_package_worker", e)
            if (runAttemptCount < MAX_DOWNLOAD_ATTEMPTS - 1) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "article-offline-packages"
        internal const val MAX_DOWNLOAD_ATTEMPTS = 3

        fun enqueue(
            context: Context,
            replaceExisting: Boolean = false,
            wiFiOnly: Boolean = false,
            requiresCharging: Boolean = false,
        ) {
            val policy = if (replaceExisting) {
                ExistingWorkPolicy.REPLACE
            } else {
                ExistingWorkPolicy.KEEP
            }

            val request = OneTimeWorkRequestBuilder<ArticleOfflinePackageWorker>()
                .setConstraints(offlinePackageConstraints(wiFiOnly, requiresCharging))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                policy,
                request,
            )
        }
    }
}

internal fun offlinePackageConstraints(
    wiFiOnly: Boolean,
    requiresCharging: Boolean,
): Constraints {
    return Constraints.Builder()
        .setRequiredNetworkType(
            if (wiFiOnly) {
                NetworkType.UNMETERED
            } else {
                NetworkType.CONNECTED
            }
        )
        .setRequiresCharging(requiresCharging)
        .build()
}
