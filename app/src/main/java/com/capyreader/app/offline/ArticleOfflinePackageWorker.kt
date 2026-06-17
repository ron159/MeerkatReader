package com.capyreader.app.offline

import android.content.Context
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

class ArticleOfflinePackageWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams), KoinComponent {
    private val downloader by inject<ArticleOfflinePackageDownloader>()

    override suspend fun doWork(): Result {
        return try {
            downloader.queueAlwaysKeepOffline()
            val result = downloader.downloadQueued()
            downloader.cleanup()
            if (result.shouldRetry) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CapyLog.error("article_offline_package_worker", e)
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "article-offline-packages"

        fun enqueue(
            context: Context,
            replaceExisting: Boolean = false,
            wiFiOnly: Boolean = false,
        ) {
            val policy = if (replaceExisting) {
                ExistingWorkPolicy.REPLACE
            } else {
                ExistingWorkPolicy.KEEP
            }

            val request = OneTimeWorkRequestBuilder<ArticleOfflinePackageWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(
                            if (wiFiOnly) {
                                NetworkType.UNMETERED
                            } else {
                                NetworkType.CONNECTED
                            }
                        )
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                policy,
                request,
            )
        }
    }
}
