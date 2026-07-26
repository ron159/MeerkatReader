package com.capyreader.app.ui.articles

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.capyreader.app.ui.articles.list.OfflineFailureMenuItem
import com.capyreader.app.ui.articles.list.RetryOfflineMenuItem
import com.capyreader.app.ui.theme.CapyTheme
import com.jocmp.capy.ArticleOfflinePackageState
import com.jocmp.capy.persistence.ArticleOfflinePackageRecord
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ArticleAccessibilitySemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `loading AI preview is one polite status`() {
        setAiPreview(ArticleAiPreviewState(isLoading = true))

        composeRule
            .onNodeWithContentDescription("AI Preview Summary. Loading AI summary…")
            .assertHasPoliteLiveRegion()
    }

    @Test
    fun `failed AI preview is one polite status`() {
        setAiPreview(ArticleAiPreviewState(error = "Provider details stay hidden"))

        composeRule
            .onNodeWithContentDescription("AI Preview Summary. AI summary failed")
            .assertHasPoliteLiveRegion()
    }

    @Test
    fun `fresh AI preview exposes ready state`() {
        setAiPreview(ArticleAiPreviewState(result = "Fresh summary"))

        composeRule
            .onNodeWithContentDescription(
                "AI Preview Summary. AI summary ready. Fresh summary"
            )
            .assertExists()
    }

    @Test
    fun `cached AI preview exposes cached state`() {
        setAiPreview(
            ArticleAiPreviewState(
                result = "Cached summary",
                isCached = true,
            )
        )
        composeRule
            .onNodeWithContentDescription(
                "AI Preview Summary. Cached AI summary. Cached summary"
            )
            .assertExists()
    }

    @Test
    fun `offline ready icon announces status politely`() {
        composeRule.setContent {
            CapyTheme {
                OfflineStatusIcon(
                    state = ArticleOfflinePackageState.READY,
                    tint = Color.Black,
                    fontScale = ArticleListFontScale.MEDIUM,
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("Available offline")
            .assertHasPoliteLiveRegion()
    }

    @Test
    fun `offline failure detail is informational and retry remains actionable`() {
        var retried = false
        composeRule.setContent {
            CapyTheme {
                OfflineFailureMenuItem(
                    onDismissRequest = {},
                    offlinePackageRecord = offlineRecord(
                        state = ArticleOfflinePackageState.FAILED,
                        errorMessage = "Network unavailable",
                    ),
                )
                RetryOfflineMenuItem(
                    onDismissRequest = {},
                    onRetryOffline = { retried = true },
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("Network unavailable")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        composeRule
            .onNodeWithText("Retry offline download")
            .assertHasClickAction()
            .performClick()

        assertTrue(retried)
    }

    private fun setAiPreview(state: ArticleAiPreviewState) {
        composeRule.setContent {
            CapyTheme {
                ArticleAiSummaryPreview(
                    state = state,
                    deEmphasizeFontWeight = false,
                )
            }
        }
    }

    private fun offlineRecord(
        state: ArticleOfflinePackageState,
        errorMessage: String?,
    ) = ArticleOfflinePackageRecord(
        articleID = "article",
        state = state,
        includeFullContent = true,
        includeImages = true,
        includeAudio = false,
        bytes = 0,
        errorMessage = errorMessage,
        updatedAt = 0,
    )

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertHasPoliteLiveRegion() {
        assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.LiveRegion,
                LiveRegionMode.Polite,
            )
        )
    }
}
