package com.capyreader.app.articleimages

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.preferences.ArticleImageDownloadMode

class ArticleImagePreloader(
    private val context: Context,
    private val appPreferences: AppPreferences,
) {
    fun enqueue(replaceExisting: Boolean = false) {
        val mode = appPreferences.articleImageDownloadMode.get()
        if (mode == ArticleImageDownloadMode.OFF) {
            return
        }

        val policy = if (replaceExisting) {
            ExistingWorkPolicy.REPLACE
        } else {
            ExistingWorkPolicy.KEEP
        }

        val request = OneTimeWorkRequestBuilder<ArticleImageDownloadWorker>()
            .setConstraints(mode.constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            policy,
            request,
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private val ArticleImageDownloadMode.constraints: Constraints
        get() {
            val networkType = when (this) {
                ArticleImageDownloadMode.OFF -> NetworkType.NOT_REQUIRED
                ArticleImageDownloadMode.WIFI_ONLY -> NetworkType.UNMETERED
                ArticleImageDownloadMode.ALWAYS -> NetworkType.CONNECTED
            }

            return Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .build()
        }

    companion object {
        private const val WORK_NAME = "article-image-downloads"
    }
}
