package com.capyreader.app.ui.settings.filters

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.capyreader.app.ai.ArticleAiErrorReason
import com.capyreader.app.ai.ArticleAiRuleRunAvailability
import com.capyreader.app.ai.ArticleAiRuleRunState
import com.capyreader.app.ai.ArticleAiRuleRunStatus
import com.capyreader.app.ui.theme.CapyTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class AiRuleRunStatusItemTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `status is absent when no AI rule exists`() {
        setStatus(hasAiRules = false)

        composeRule
            .onNodeWithText("AI rule activity")
            .assertDoesNotExist()
    }

    @Test
    fun `ready status exposes one explicit run action`() {
        var ran = false
        setStatus(onRun = { ran = true })

        composeRule
            .onNodeWithText("Not run yet")
            .assertExists()
        composeRule
            .onNodeWithText("Run now")
            .assertHasClickAction()
            .performClick()

        assertTrue(ran)
    }

    @Test
    fun `partial failure exposes redacted reason metrics and retry`() {
        var retried = false
        setStatus(
            status = ArticleAiRuleRunStatus(
                state = ArticleAiRuleRunState.PARTIAL_FAILURE,
                completedAtEpochSeconds = 1_750_000_000,
                candidatesChecked = 3,
                providerAttempts = 2,
                cacheHits = 1,
                validDecisions = 1,
                matches = 1,
                remainingDailyAllowance = 18,
                failureCount = 1,
                failureReason = ArticleAiErrorReason.RATE_LIMIT,
            ),
            onRun = { retried = true },
        )

        composeRule
            .onNodeWithText(
                "Checked: 3 · Requests: 2 · Cached: 1 · Decisions: 1 · " +
                    "Matches: 1 · Remaining today: 18",
                substring = true,
            )
            .assertExists()
        composeRule
            .onNodeWithText(
                "The AI provider rate limit was reached. Wait and try again.",
                substring = true,
            )
            .assertExists()
        composeRule
            .onNodeWithText("Retry")
            .assertHasClickAction()
            .performClick()

        assertTrue(retried)
    }

    @Test
    fun `queued status is announced and has no duplicate action`() {
        setStatus(
            status = ArticleAiRuleRunStatus(
                state = ArticleAiRuleRunState.QUEUED,
            )
        )

        composeRule
            .onNodeWithText("Waiting for an unmetered network")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                )
            )
        composeRule
            .onNodeWithText("Run now")
            .assertDoesNotExist()
    }

    @Test
    fun `missing provider key is visible without an action`() {
        setStatus(
            availability = ArticleAiRuleRunAvailability.API_KEY_REQUIRED,
        )

        composeRule
            .onNodeWithText("API key is required", substring = true)
            .assertExists()
        composeRule
            .onNodeWithText("Run now")
            .assertDoesNotExist()
    }

    private fun setStatus(
        hasAiRules: Boolean = true,
        status: ArticleAiRuleRunStatus = ArticleAiRuleRunStatus.Never,
        availability: ArticleAiRuleRunAvailability =
            ArticleAiRuleRunAvailability.READY,
        onRun: () -> Unit = {},
    ) {
        composeRule.setContent {
            CapyTheme {
                AiRuleRunStatusItem(
                    hasAiRules = hasAiRules,
                    status = status,
                    availability = availability,
                    onRun = onRun,
                )
            }
        }
    }
}
