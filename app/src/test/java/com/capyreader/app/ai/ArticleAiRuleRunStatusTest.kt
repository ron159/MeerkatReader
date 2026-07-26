package com.capyreader.app.ai

import android.content.Context
import androidx.preference.PreferenceManager
import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.preferences.DEVICE_STATE_PREFERENCES_NAME
import com.capyreader.app.preferences.InMemorySecretStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ArticleAiRuleRunStatusTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()
    private lateinit var preferences: AppPreferences
    private var now = 100L

    @Before
    fun setUp() {
        preferences = AppPreferences(context, InMemorySecretStore())
        preferences.clearAll()
    }

    @Test
    fun `run lifecycle persists compact mixed-success summary`() {
        val store = store()

        store.recordQueued(remainingDailyAllowance = 20)
        assertEquals(ArticleAiRuleRunState.QUEUED, store.current().state)
        assertEquals(100L, store.current().startedAtEpochSeconds)

        now = 110L
        store.recordRunning(remainingDailyAllowance = 20)
        assertEquals(ArticleAiRuleRunState.RUNNING, store.current().state)
        assertEquals(100L, store.current().startedAtEpochSeconds)

        now = 120L
        store.recordCompleted(
            run = ArticleAiRuleRun(
                candidatesChecked = 4,
                cached = 1,
                requested = 3,
                evaluated = 2,
                matched = 1,
                failures = listOf(
                    ArticleAiRuleFailure(
                        articleID = "article-secret",
                        ruleID = "rule-secret",
                        error = ArticleAiException(ArticleAiErrorReason.RATE_LIMIT),
                    )
                ),
            ),
            remainingDailyAllowance = 17,
        )

        assertEquals(
            ArticleAiRuleRunStatus(
                state = ArticleAiRuleRunState.PARTIAL_FAILURE,
                startedAtEpochSeconds = 100,
                completedAtEpochSeconds = 120,
                candidatesChecked = 4,
                providerAttempts = 3,
                cacheHits = 1,
                validDecisions = 2,
                matches = 1,
                remainingDailyAllowance = 17,
                failureCount = 1,
                failureReason = ArticleAiErrorReason.RATE_LIMIT,
            ),
            store.current(),
        )
        val raw = preferences.aiOptions.ruleEvaluationRunStatus.get()
        assertFalse(raw.contains("article-secret"))
        assertFalse(raw.contains("rule-secret"))
    }

    @Test
    fun `unknown failure persists only generic redacted reason`() {
        val store = store()

        store.recordFailure(
            error = IllegalStateException("provider body and secret token"),
            remainingDailyAllowance = 7,
        )

        val status = store.current()
        assertEquals(ArticleAiRuleRunState.FAILED, status.state)
        assertEquals(ArticleAiErrorReason.REQUEST_FAILED, status.failureReason)
        assertEquals(1, status.failureCount)
        val raw = preferences.aiOptions.ruleEvaluationRunStatus.get()
        assertFalse(raw.contains("provider body"))
        assertFalse(raw.contains("secret token"))
    }

    @Test
    fun `daily-limit and corrupt-state handling are stable`() {
        val store = store()
        store.recordDailyLimitReached()

        assertEquals(
            ArticleAiRuleRunState.DAILY_LIMIT_REACHED,
            store.current().state,
        )
        assertEquals(0, store.current().remainingDailyAllowance)

        preferences.aiOptions.ruleEvaluationRunStatus.set("{not-json")
        assertEquals(ArticleAiRuleRunStatus.Never, store.current())
    }

    @Test
    fun `operational usage and status live outside portable preferences`() {
        preferences.aiOptions.backgroundPreviewsDailyUsage.set("2026-07-25|2")
        preferences.aiOptions.ruleEvaluationsDailyUsage.set("2026-07-25|3")
        store().recordQueued(remainingDailyAllowance = 17)

        val portable = PreferenceManager.getDefaultSharedPreferences(context)
        assertFalse(
            portable.contains("ai_background_preview_summaries_daily_usage")
        )
        assertFalse(portable.contains("ai_rule_evaluations_daily_usage"))
        assertFalse(portable.contains("ai_rule_evaluation_run_status"))

        val deviceState = context.getSharedPreferences(
            DEVICE_STATE_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        assertTrue(
            deviceState.contains("ai_background_preview_summaries_daily_usage")
        )
        assertTrue(deviceState.contains("ai_rule_evaluations_daily_usage"))
        assertTrue(deviceState.contains("ai_rule_evaluation_run_status"))

        preferences.clearAll()

        assertFalse(
            deviceState.contains("ai_background_preview_summaries_daily_usage")
        )
        assertFalse(deviceState.contains("ai_rule_evaluations_daily_usage"))
        assertFalse(deviceState.contains("ai_rule_evaluation_run_status"))
    }

    private fun store() = ArticleAiRuleRunStatusStore(
        aiOptions = preferences.aiOptions,
        nowEpochSeconds = { now },
    )
}
