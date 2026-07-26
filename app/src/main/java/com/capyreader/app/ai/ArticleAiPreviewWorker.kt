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

        if (!aiOptions.canRunBackgroundPreviews()) {
            return Result.success()
        }

        return try {
            val dailyBudget = ArticleAiDailyBudget(aiOptions)
            val remaining = dailyBudget.remaining()
            if (remaining == 0) {
                CapyLog.info(
                    "article_ai_preview_worker:daily_limit_reached",
                    mapOf("daily_limit" to aiOptions.backgroundPreviewsDailyLimit.get()),
                )
                return Result.success()
            }

            val articles = account.latestArticles(limit = MAX_CANDIDATES.toLong()).first()
            val run = ArticleAiPreviewProcessor(
                articleAiRepository = articleAiRepository,
                dailyBudget = dailyBudget,
            ).process(
                articles = articles,
                maxRequests = MAX_REQUESTS_PER_RUN,
                maxSuccesses = minOf(MAX_REQUESTS_PER_RUN, remaining),
            )

            run.failures.forEach { failure ->
                CapyLog.warn(
                    "article_ai_preview_worker",
                    mapOf(
                        "article_id" to failure.articleID,
                        "error_type" to failure.error::class.simpleName,
                        "error_reason" to
                            (failure.error as? ArticleAiException)?.reason?.name,
                    )
                )
            }

            CapyLog.info(
                "article_ai_preview_worker:success",
                mapOf(
                    "cached" to run.cached,
                    "requested" to run.requested,
                    "generated" to run.generated,
                    "remaining" to dailyBudget.remaining(),
                ),
            )
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
        private const val MAX_REQUESTS_PER_RUN = 10
        private const val MAX_CANDIDATES = 30

        fun enqueue(
            context: Context,
            wiFiOnly: Boolean = true,
            requiresCharging: Boolean = false,
        ) {
            val request = OneTimeWorkRequestBuilder<ArticleAiPreviewWorker>()
                .setConstraints(articleAiPreviewConstraints(wiFiOnly, requiresCharging))
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}

internal fun articleAiPreviewConstraints(
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

internal fun AppPreferences.AiOptions.canRunBackgroundPreviews(): Boolean {
    return enabled.get() &&
        backgroundPreviewsEnabled.get() &&
        apiKey.get().isNotBlank()
}
