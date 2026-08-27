package com.capyreader.app.ui.articles

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.capyreader.app.common.ImagePreview
import com.capyreader.app.ui.fixtures.PreviewKoinApplication
import com.capyreader.app.ui.theme.CapyTheme
import com.jocmp.capy.Article
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.koin.core.context.stopKoin

@RunWith(RobolectricTestRunner::class)
@Config(
    application = android.app.Application::class,
    qualifiers = "en-rUS-w360dp-h640dp",
)
class ArticleRowImagePositionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `inline image stays to the right by default`() {
        setArticleRow(imagePreviewOnLeft = false)

        val imageBounds = composeRule
            .onNodeWithTag(ARTICLE_ROW_IMAGE_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val titleBounds = composeRule
            .onNodeWithText(TITLE, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(imageBounds.left > titleBounds.left)
    }

    @Test
    fun `inline image moves before article text when left placement is enabled`() {
        setArticleRow(imagePreviewOnLeft = true)

        val imageBounds = composeRule
            .onNodeWithTag(ARTICLE_ROW_IMAGE_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val titleBounds = composeRule
            .onNodeWithText(TITLE, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(imageBounds.right < titleBounds.left)
    }

    private fun setArticleRow(imagePreviewOnLeft: Boolean) {
        composeRule.setContent {
            PreviewKoinApplication {
                CapyTheme {
                    ArticleRow(
                        article = article,
                        index = 0,
                        selected = false,
                        onSelect = {},
                        currentTime = LocalDateTime.now(),
                        options = ArticleRowOptions(
                            imagePreview = ImagePreview.MEDIUM,
                            imagePreviewOnLeft = imagePreviewOnLeft,
                            showSummary = false,
                        ),
                    )
                }
            }
        }
    }

    private companion object {
        const val ARTICLE_ROW_IMAGE_TAG = "article-row-image"
        const val TITLE = "Article image placement test"

        val article = Article(
            id = "article-image-position",
            feedID = "feed",
            title = TITLE,
            author = "Author",
            contentHTML = "<p>Content</p>",
            imageURL = "https://example.com/image.jpg",
            summary = "Summary",
            url = URL("https://example.com/article"),
            updatedAt = ZonedDateTime.of(2026, 8, 27, 8, 0, 0, 0, ZoneOffset.UTC),
            publishedAt = ZonedDateTime.of(2026, 8, 27, 8, 0, 0, 0, ZoneOffset.UTC),
            read = false,
            starred = false,
            feedName = "Example Feed",
        )
    }
}
