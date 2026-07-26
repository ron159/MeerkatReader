package com.capyreader.app.ui.settings.filters

import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.capyreader.app.R
import com.capyreader.app.ai.ArticleAiErrorReason
import com.capyreader.app.ai.ArticleAiRuleRunAvailability
import com.capyreader.app.ai.ArticleAiRuleRunState
import com.capyreader.app.ai.ArticleAiRuleRunStatus
import java.text.DateFormat
import java.util.Date

@Composable
internal fun AiRuleRunStatusItem(
    hasAiRules: Boolean,
    status: ArticleAiRuleRunStatus,
    availability: ArticleAiRuleRunAvailability,
    onRun: () -> Unit,
) {
    if (!hasAiRules) {
        return
    }

    val outcome = status.outcomeText()
    val metrics = status.metricsText()
    val failure = status.failureReason?.message()
    val unavailable = availability.unavailableMessage()
    val detail = listOfNotNull(outcome, metrics, failure, unavailable)
        .distinct()
        .joinToString("\n")
    val canRun = availability == ArticleAiRuleRunAvailability.READY &&
        !status.isActive
    val isRetry = status.state == ArticleAiRuleRunState.PARTIAL_FAILURE ||
        status.state == ArticleAiRuleRunState.FAILED

    ListItem(
        headlineContent = {
            Text(stringResource(R.string.rules_ai_run_title))
        },
        supportingContent = {
            Text(
                text = detail,
                modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                },
            )
        },
        trailingContent = {
            if (canRun) {
                TextButton(onClick = onRun) {
                    Text(
                        stringResource(
                            if (isRetry) {
                                R.string.rules_ai_run_retry
                            } else {
                                R.string.rules_ai_run_now
                            }
                        )
                    )
                }
            }
        },
    )
}

@Composable
private fun ArticleAiRuleRunStatus.outcomeText(): String {
    return when (state) {
        ArticleAiRuleRunState.NEVER ->
            stringResource(R.string.rules_ai_run_never)
        ArticleAiRuleRunState.QUEUED ->
            stringResource(R.string.rules_ai_run_queued)
        ArticleAiRuleRunState.RUNNING ->
            stringResource(R.string.rules_ai_run_running)
        ArticleAiRuleRunState.SUCCEEDED ->
            stringResource(
                R.string.rules_ai_run_completed,
                formattedCompletionTime(),
            )
        ArticleAiRuleRunState.PARTIAL_FAILURE ->
            stringResource(
                R.string.rules_ai_run_partial,
                failureCount,
                formattedCompletionTime(),
            )
        ArticleAiRuleRunState.FAILED ->
            stringResource(
                R.string.rules_ai_run_failed,
                formattedCompletionTime(),
            )
        ArticleAiRuleRunState.DAILY_LIMIT_REACHED ->
            stringResource(R.string.rules_ai_run_daily_limit)
    }
}

@Composable
private fun ArticleAiRuleRunStatus.metricsText(): String? {
    if (
        state != ArticleAiRuleRunState.SUCCEEDED &&
        state != ArticleAiRuleRunState.PARTIAL_FAILURE
    ) {
        return null
    }

    return stringResource(
        R.string.rules_ai_run_metrics,
        candidatesChecked,
        providerAttempts,
        cacheHits,
        validDecisions,
        matches,
        remainingDailyAllowance,
    )
}

@Composable
private fun ArticleAiRuleRunStatus.formattedCompletionTime(): String {
    val epochSeconds = completedAtEpochSeconds.coerceAtLeast(0)
    return remember(epochSeconds) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(epochSeconds * 1_000))
    }
}

@Composable
private fun ArticleAiRuleRunAvailability.unavailableMessage(): String? {
    return when (this) {
        ArticleAiRuleRunAvailability.READY,
        ArticleAiRuleRunAvailability.NO_AI_RULES -> null
        ArticleAiRuleRunAvailability.NO_ENABLED_AI_RULES ->
            stringResource(R.string.rules_ai_run_no_enabled_rules)
        ArticleAiRuleRunAvailability.AI_DISABLED ->
            stringResource(R.string.article_ai_error_disabled)
        ArticleAiRuleRunAvailability.API_KEY_REQUIRED ->
            stringResource(R.string.article_ai_error_api_key_required)
        ArticleAiRuleRunAvailability.MODEL_REQUIRED ->
            stringResource(R.string.article_ai_error_model_required)
        ArticleAiRuleRunAvailability.DAILY_LIMIT_REACHED ->
            stringResource(R.string.rules_ai_run_daily_limit)
    }
}

@Composable
private fun ArticleAiErrorReason.message(): String {
    return stringResource(
        when (this) {
            ArticleAiErrorReason.DISABLED ->
                R.string.article_ai_error_disabled
            ArticleAiErrorReason.DISABLED_FOR_FEED ->
                R.string.article_ai_error_disabled_for_feed
            ArticleAiErrorReason.API_KEY_REQUIRED ->
                R.string.article_ai_error_api_key_required
            ArticleAiErrorReason.MODEL_REQUIRED ->
                R.string.article_ai_error_model_required
            ArticleAiErrorReason.CONTENT_EMPTY ->
                R.string.article_ai_error_content_empty
            ArticleAiErrorReason.QUESTION_REQUIRED ->
                R.string.article_ai_error_question_required
            ArticleAiErrorReason.NO_DIGEST_ARTICLES ->
                R.string.article_ai_error_no_digest_articles
            ArticleAiErrorReason.INVALID_CONFIGURATION ->
                R.string.article_ai_error_invalid_configuration
            ArticleAiErrorReason.AUTHENTICATION ->
                R.string.article_ai_error_authentication
            ArticleAiErrorReason.RATE_LIMIT ->
                R.string.article_ai_error_rate_limit
            ArticleAiErrorReason.TIMEOUT ->
                R.string.article_ai_error_timeout
            ArticleAiErrorReason.CONNECTIVITY ->
                R.string.article_ai_error_connectivity
            ArticleAiErrorReason.SERVER ->
                R.string.article_ai_error_server
            ArticleAiErrorReason.PROVIDER_REJECTED ->
                R.string.article_ai_error_provider_rejected
            ArticleAiErrorReason.INVALID_RESPONSE ->
                R.string.article_ai_error_invalid_response
            ArticleAiErrorReason.REQUEST_FAILED ->
                R.string.article_ai_error_request_failed
        }
    )
}
