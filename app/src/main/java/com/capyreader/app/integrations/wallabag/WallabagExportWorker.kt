package com.capyreader.app.integrations.wallabag

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

class WallabagExportWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams), KoinComponent {
    private val exporter by inject<WallabagArticleExporter>()

    override suspend fun doWork(): Result {
        if (!exporter.isConfigured()) {
            return Result.success()
        }

        return try {
            val result = exporter.exportQueued()
            if (result.shouldRetry) Result.retry() else Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CapyLog.error("wallabag_export_worker", e)
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "wallabag-exports"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<WallabagExportWorker>()
                .setConstraints(wallabagExportConstraints())
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}

internal fun wallabagExportConstraints(): Constraints {
    return Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
}
