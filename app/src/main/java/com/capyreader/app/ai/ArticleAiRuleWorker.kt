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
import com.jocmp.capy.ArticleAutomation
import com.jocmp.capy.ArticleAutomationRule
import com.jocmp.capy.latestArticles
import com.jocmp.capy.logging.CapyLog
import com.jocmp.capy.persistence.ArticleAiResultRecords
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ArticleAiRuleWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams), KoinComponent {
    private val account by inject<Account>()
    private val appPreferences by inject<AppPreferences>()
    private val chatClient by inject<AiChatClient>()
    private val resultRecords by inject<ArticleAiResultRecords>()

    override suspend fun doWork(): Result {
        val rules = account.preferences.automationRules.get()
        val budget = ArticleAiRuleDailyBudget(appPreferences.aiOptions)
        val statusStore = ArticleAiRuleRunStatusStore(appPreferences.aiOptions)
        val availability = articleAiRuleRunAvailability(
            aiOptions = appPreferences.aiOptions,
            rules = rules,
        )
        if (availability != ArticleAiRuleRunAvailability.READY) {
            if (availability == ArticleAiRuleRunAvailability.DAILY_LIMIT_REACHED) {
                statusStore.recordDailyLimitReached()
            } else {
                statusStore.recordFailure(
                    error = ArticleAiException(availability.errorReason()),
                    remainingDailyAllowance = budget.remaining(),
                )
            }
            return Result.success()
        }

        return try {
            statusStore.recordRunning(budget.remaining())
            val automation = ArticleAutomation(
                database = account.database,
                preferences = account.preferences,
            )
            val run = ArticleAiRuleProcessor(
                decisionSource = ArticleAiRuleEvaluator(
                    appPreferences = appPreferences,
                    account = account,
                    chatClient = chatClient,
                    resultRecords = resultRecords,
                ),
                dailyBudget = budget,
                matchApplier = ArticleAiRuleMatchApplier(
                    account = account,
                    articleAutomation = automation,
                ),
            ).process(
                rules = rules,
                articles = account.latestArticles(limit = MAX_CANDIDATES.toLong()).first(),
                maxRequests = MAX_REQUESTS_PER_RUN,
            )
            statusStore.recordCompleted(
                run = run,
                remainingDailyAllowance = budget.remaining(),
            )

            run.failures.forEach { failure ->
                CapyLog.warn(
                    "article_ai_rule_worker",
                    mapOf(
                        "article_id" to failure.articleID,
                        "rule_id" to failure.ruleID,
                        "error_type" to failure.error::class.simpleName,
                        "error_reason" to
                            (failure.error as? ArticleAiException)?.reason?.name,
                    ),
                )
            }
            CapyLog.info(
                "article_ai_rule_worker:success",
                mapOf(
                    "cached" to run.cached,
                    "requested" to run.requested,
                    "evaluated" to run.evaluated,
                    "matched" to run.matched,
                    "remaining" to budget.remaining(),
                ),
            )
            Result.success()
        } catch (error: CancellationException) {
            statusStore.recordFailure(
                error = error,
                remainingDailyAllowance = budget.remaining(),
            )
            throw error
        } catch (error: Exception) {
            statusStore.recordFailure(
                error = error,
                remainingDailyAllowance = budget.remaining(),
            )
            CapyLog.error("article_ai_rule_worker", error)
            if (shouldRetryArticleAiRuleWork(runAttemptCount)) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        internal const val MAX_REQUESTS_PER_RUN = 10
        private const val MAX_CANDIDATES = 30
        internal const val WORK_NAME = "article-ai-rules"

        fun enqueueIfEligible(
            context: Context,
            appPreferences: AppPreferences,
            rules: () -> List<ArticleAutomationRule>,
        ): ArticleAiRuleRunAvailability {
            val aiOptions = appPreferences.aiOptions
            val providerAvailability = aiOptions.providerAvailability()
            if (providerAvailability != ArticleAiRuleRunAvailability.READY) {
                return providerAvailability
            }
            val activeRules = rules()
            val availability = articleAiRuleRunAvailability(
                aiOptions = aiOptions,
                rules = activeRules,
            )
            if (availability != ArticleAiRuleRunAvailability.READY) {
                return availability
            }

            val request = OneTimeWorkRequestBuilder<ArticleAiRuleWorker>()
                .setConstraints(articleAiRuleConstraints())
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
            ArticleAiRuleRunStatusStore(aiOptions).recordQueued(
                remainingDailyAllowance = ArticleAiRuleDailyBudget(aiOptions).remaining()
            )
            return ArticleAiRuleRunAvailability.READY
        }
    }
}

internal fun articleAiRuleConstraints(): Constraints {
    return Constraints.Builder()
        .setRequiredNetworkType(NetworkType.UNMETERED)
        .build()
}

internal fun AppPreferences.AiOptions.canRunArticleAiRules(
    rules: List<ArticleAutomationRule>,
): Boolean {
    return providerAvailability() == ArticleAiRuleRunAvailability.READY &&
        rules.any {
            it.enabled &&
                it.aiEnabled &&
                it.aiCriterion.isNotBlank() &&
                it.actions.isNotEmpty()
        }
}

fun articleAiRuleRunAvailability(
    aiOptions: AppPreferences.AiOptions,
    rules: List<ArticleAutomationRule>,
): ArticleAiRuleRunAvailability {
    val aiRules = rules.filter {
        it.aiEnabled && it.aiCriterion.isNotBlank()
    }
    if (aiRules.isEmpty()) {
        return ArticleAiRuleRunAvailability.NO_AI_RULES
    }
    if (aiRules.none { it.enabled && it.actions.isNotEmpty() }) {
        return ArticleAiRuleRunAvailability.NO_ENABLED_AI_RULES
    }

    val providerAvailability = aiOptions.providerAvailability()
    if (providerAvailability != ArticleAiRuleRunAvailability.READY) {
        return providerAvailability
    }

    return if (ArticleAiRuleDailyBudget(aiOptions).remaining() == 0) {
        ArticleAiRuleRunAvailability.DAILY_LIMIT_REACHED
    } else {
        ArticleAiRuleRunAvailability.READY
    }
}

private fun AppPreferences.AiOptions.providerAvailability(): ArticleAiRuleRunAvailability {
    return when {
        !enabled.get() -> ArticleAiRuleRunAvailability.AI_DISABLED
        apiKey.get().isBlank() -> ArticleAiRuleRunAvailability.API_KEY_REQUIRED
        model.get().isBlank() -> ArticleAiRuleRunAvailability.MODEL_REQUIRED
        else -> ArticleAiRuleRunAvailability.READY
    }
}

internal fun shouldRetryArticleAiRuleWork(runAttemptCount: Int): Boolean {
    return runAttemptCount < MAX_ARTICLE_AI_RULE_WORK_ATTEMPTS - 1
}

enum class ArticleAiRuleRunAvailability {
    READY,
    NO_AI_RULES,
    NO_ENABLED_AI_RULES,
    AI_DISABLED,
    API_KEY_REQUIRED,
    MODEL_REQUIRED,
    DAILY_LIMIT_REACHED,
}

private fun ArticleAiRuleRunAvailability.errorReason(): ArticleAiErrorReason {
    return when (this) {
        ArticleAiRuleRunAvailability.AI_DISABLED,
        ArticleAiRuleRunAvailability.NO_AI_RULES,
        ArticleAiRuleRunAvailability.NO_ENABLED_AI_RULES ->
            ArticleAiErrorReason.DISABLED
        ArticleAiRuleRunAvailability.API_KEY_REQUIRED ->
            ArticleAiErrorReason.API_KEY_REQUIRED
        ArticleAiRuleRunAvailability.MODEL_REQUIRED ->
            ArticleAiErrorReason.MODEL_REQUIRED
        ArticleAiRuleRunAvailability.READY,
        ArticleAiRuleRunAvailability.DAILY_LIMIT_REACHED ->
            ArticleAiErrorReason.REQUEST_FAILED
    }
}

private const val MAX_ARTICLE_AI_RULE_WORK_ATTEMPTS = 3
