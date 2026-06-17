package com.capyreader.app.ai

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.capyreader.app.preferences.AppPreferences
import com.jocmp.capy.Account
import com.jocmp.capy.latestArticles
import com.jocmp.capy.logging.CapyLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ArticleAiPreviewWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams), KoinComponent {
    private val account by inject<Account>()
    private val appPreferences by inject<AppPreferences>()
    private val articleAiRepository by inject<ArticleAiRepository>()

    override suspend fun doWork(): Result {
        val aiOptions = appPreferences.aiOptions

        if (!aiOptions.enabled.get() ||
            !aiOptions.backgroundPreviewsEnabled.get() ||
            aiOptions.apiKey.get().isBlank()
        ) {
            return Result.success()
        }

        return try {
            val articles = account.latestArticles(limit = DEFAULT_LIMIT.toLong()).first()
            var processed = 0

            articles.forEach { article ->
                if (!articleAiRepository.cachedResult(ArticleAiAction.PREVIEW_SUMMARY, article).isNullOrBlank()) {
                    return@forEach
                }

                articleAiRepository.run(
                    action = ArticleAiAction.PREVIEW_SUMMARY,
                    article = article,
                    forceRefresh = false,
                ).onSuccess {
                    processed += 1
                }.onFailure { error ->
                    CapyLog.warn(
                        "article_ai_preview_worker",
                        mapOf(
                            "article_id" to article.id,
                            "error_type" to error::class.simpleName,
                            "error_message" to error.message,
                        )
                    )
                }
            }

            CapyLog.info("article_ai_preview_worker:success", mapOf("processed" to processed))
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CapyLog.error("article_ai_preview_worker", e)
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "article-ai-previews"
        private const val DEFAULT_LIMIT = 10

        fun enqueue(
            context: Context,
            wiFiOnly: Boolean = true,
        ) {
            val request = OneTimeWorkRequestBuilder<ArticleAiPreviewWorker>()
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
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
