package com.capyreader.app.articleimages

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jocmp.capy.logging.CapyLog
import kotlinx.coroutines.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ArticleImageDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams), KoinComponent {
    private val downloader by inject<ArticleImageDownloader>()
    private val cleaner by inject<ArticleImageCacheCleaner>()

    override suspend fun doWork(): Result {
        return try {
            downloader.downloadPending()
            cleaner.cleanup()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CapyLog.error("article_image_download_worker", e)
            Result.retry()
        }
    }
}
