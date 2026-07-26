package com.capyreader.app.ai

import com.capyreader.app.preferences.AppPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

@Serializable
data class ArticleAiRuleRunStatus(
    val state: ArticleAiRuleRunState = ArticleAiRuleRunState.NEVER,
    val startedAtEpochSeconds: Long = 0,
    val completedAtEpochSeconds: Long = 0,
    val candidatesChecked: Int = 0,
    val providerAttempts: Int = 0,
    val cacheHits: Int = 0,
    val validDecisions: Int = 0,
    val matches: Int = 0,
    val remainingDailyAllowance: Int = 0,
    val failureCount: Int = 0,
    val failureReason: ArticleAiErrorReason? = null,
) {
    val isActive: Boolean
        get() = state == ArticleAiRuleRunState.QUEUED ||
            state == ArticleAiRuleRunState.RUNNING

    companion object {
        val Never = ArticleAiRuleRunStatus()

        fun deserialize(value: String): ArticleAiRuleRunStatus {
            if (value.isBlank()) {
                return Never
            }

            return runCatching {
                statusJson.decodeFromString<ArticleAiRuleRunStatus>(value)
            }.getOrDefault(Never)
        }
    }
}

@Serializable
enum class ArticleAiRuleRunState {
    NEVER,
    QUEUED,
    RUNNING,
    SUCCEEDED,
    PARTIAL_FAILURE,
    FAILED,
    DAILY_LIMIT_REACHED,
}

internal class ArticleAiRuleRunStatusStore(
    private val aiOptions: AppPreferences.AiOptions,
    private val nowEpochSeconds: () -> Long = { Instant.now().epochSecond },
) {
    fun current(): ArticleAiRuleRunStatus {
        return ArticleAiRuleRunStatus.deserialize(
            aiOptions.ruleEvaluationRunStatus.get()
        )
    }

    fun recordQueued(remainingDailyAllowance: Int) {
        val current = current()
        if (current.isActive) {
            return
        }

        persist(
            ArticleAiRuleRunStatus(
                state = ArticleAiRuleRunState.QUEUED,
                startedAtEpochSeconds = nowEpochSeconds(),
                remainingDailyAllowance = remainingDailyAllowance,
            )
        )
    }

    fun recordRunning(remainingDailyAllowance: Int) {
        val current = current()
        persist(
            ArticleAiRuleRunStatus(
                state = ArticleAiRuleRunState.RUNNING,
                startedAtEpochSeconds = current.startedAtEpochSeconds
                    .takeIf { current.isActive && it > 0 }
                    ?: nowEpochSeconds(),
                remainingDailyAllowance = remainingDailyAllowance,
            )
        )
    }

    fun recordDailyLimitReached() {
        val now = nowEpochSeconds()
        persist(
            ArticleAiRuleRunStatus(
                state = ArticleAiRuleRunState.DAILY_LIMIT_REACHED,
                startedAtEpochSeconds = now,
                completedAtEpochSeconds = now,
                remainingDailyAllowance = 0,
            )
        )
    }

    fun recordCompleted(
        run: ArticleAiRuleRun,
        remainingDailyAllowance: Int,
    ) {
        val current = current()
        val now = nowEpochSeconds()
        val state = when {
            run.failures.isEmpty() -> ArticleAiRuleRunState.SUCCEEDED
            run.evaluated > 0 || run.cached > 0 || run.matched > 0 ->
                ArticleAiRuleRunState.PARTIAL_FAILURE
            else -> ArticleAiRuleRunState.FAILED
        }
        persist(
            ArticleAiRuleRunStatus(
                state = state,
                startedAtEpochSeconds = current.startedAtEpochSeconds
                    .takeIf { current.isActive && it > 0 }
                    ?: now,
                completedAtEpochSeconds = now,
                candidatesChecked = run.candidatesChecked,
                providerAttempts = run.requested,
                cacheHits = run.cached,
                validDecisions = run.evaluated,
                matches = run.matched,
                remainingDailyAllowance = remainingDailyAllowance,
                failureCount = run.failures.size,
                failureReason = run.failures
                    .firstOrNull()
                    ?.error
                    ?.stableAiReason(),
            )
        )
    }

    fun recordFailure(
        error: Throwable,
        remainingDailyAllowance: Int,
    ) {
        val current = current()
        val now = nowEpochSeconds()
        persist(
            ArticleAiRuleRunStatus(
                state = ArticleAiRuleRunState.FAILED,
                startedAtEpochSeconds = current.startedAtEpochSeconds
                    .takeIf { current.isActive && it > 0 }
                    ?: now,
                completedAtEpochSeconds = now,
                remainingDailyAllowance = remainingDailyAllowance,
                failureCount = 1,
                failureReason = error.stableAiReason(),
            )
        )
    }

    private fun persist(status: ArticleAiRuleRunStatus) {
        aiOptions.ruleEvaluationRunStatus.set(statusJson.encodeToString(status))
    }
}

private fun Throwable.stableAiReason(): ArticleAiErrorReason {
    return (this as? ArticleAiException)?.reason
        ?: toArticleAiException().reason
}

private val statusJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}
