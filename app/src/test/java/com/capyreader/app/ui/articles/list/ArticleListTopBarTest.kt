package com.capyreader.app.ui.articles.list

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults.pinnedScrollBehavior
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.capyreader.app.ui.components.ArticleSearch
import com.capyreader.app.ui.theme.CapyTheme
import com.jocmp.capy.ArticleFilter
import com.jocmp.capy.accounts.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ArticleListTopBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `rapid tap detector triggers on the third tap and resets`() {
        val detector = RapidTapDetector(maxIntervalMillis = 300)

        assertFalse(detector.registerTap(1_000))
        assertFalse(detector.registerTap(1_200))
        assertTrue(detector.registerTap(1_400))
        assertFalse(detector.registerTap(1_500))
    }

    @Test
    fun `rapid tap detector restarts after the interval expires`() {
        val detector = RapidTapDetector(maxIntervalMillis = 300)

        assertFalse(detector.registerTap(1_000))
        assertFalse(detector.registerTap(1_200))
        assertFalse(detector.registerTap(1_501))
        assertFalse(detector.registerTap(1_700))
        assertTrue(detector.registerTap(1_900))
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun `three rapid logo taps keep jump action and show meerkat call toast`() {
        var jumpRequests = 0

        composeRule.setContent {
            CapyTheme {
                ArticleListTopBar(
                    onRequestJumpToTop = { jumpRequests += 1 },
                    onNavigateToSettings = {},
                    onSummarizeArticlePreviews = {},
                    onGenerateArticleDigest = {},
                    onMarkSearchResultsRead = {},
                    onStarSearchResults = {},
                    onSaveSearchResultsExternally = {},
                    onSaveCurrentSearch = {},
                    onFeedAdded = {},
                    showAddFeedButton = false,
                    showAiSummaryPreviewButton = false,
                    canSummarizeArticlePreviews = false,
                    isAiSummaryPreviewLoading = false,
                    showAiDigestButton = false,
                    canGenerateArticleDigest = false,
                    canSaveSearchResultsExternally = false,
                    onRemoveFolder = { _, _ -> },
                    scrollBehavior = pinnedScrollBehavior(),
                    search = ArticleSearch(),
                    filter = ArticleFilter.default(),
                    currentFeed = null,
                    feeds = emptyList(),
                    savedSearches = emptyList(),
                    folders = emptyList(),
                    source = Source.LOCAL,
                )
            }
        }

        val logo = composeRule.onNodeWithContentDescription("Meerkat Reader")
        repeat(3) {
            logo.performClick()
        }

        composeRule.runOnIdle {
            assertEquals(3, jumpRequests)
            assertEquals("吱吱吱", ShadowToast.getTextOfLatestToast())
        }
    }
}
