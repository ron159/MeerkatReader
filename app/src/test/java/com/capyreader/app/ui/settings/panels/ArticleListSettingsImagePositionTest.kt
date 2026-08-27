package com.capyreader.app.ui.settings.panels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.capyreader.app.common.ImagePreview
import com.capyreader.app.ui.articles.ArticleListFontScale
import com.capyreader.app.ui.theme.CapyTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    application = android.app.Application::class,
    qualifiers = "en-rUS-w360dp-h1000dp",
)
class ArticleListSettingsImagePositionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `inline preview exposes a full row image position switch`() {
        var updateReceived = false
        composeRule.setContent {
            var imagePreviewOnLeft by remember { mutableStateOf(false) }

            CapyTheme {
                ArticleListSettings(
                    options = options(
                        imagePreview = ImagePreview.MEDIUM,
                        imagePreviewOnLeft = imagePreviewOnLeft,
                        updateImagePreviewOnLeft = {
                            imagePreviewOnLeft = it
                            updateReceived = it
                        },
                    )
                )
            }
        }

        composeRule
            .onNodeWithText("Place images on the left")
            .assertIsEnabled()
            .assertIsOff()
            .performClick()
            .assertIsOn()
        composeRule.runOnIdle {
            assertTrue(updateReceived)
        }
    }

    @Test
    fun `image position switch is disabled for non inline previews`() {
        composeRule.setContent {
            CapyTheme {
                ArticleListSettings(
                    options = options(
                        imagePreview = ImagePreview.LARGE,
                        imagePreviewOnLeft = true,
                    )
                )
            }
        }

        composeRule
            .onNodeWithText("Place images on the left")
            .assertIsNotEnabled()
            .assertIsOn()
    }

    private fun options(
        imagePreview: ImagePreview,
        imagePreviewOnLeft: Boolean,
        updateImagePreviewOnLeft: (Boolean) -> Unit = {},
    ) = ArticleListOptions(
        imagePreview = imagePreview,
        imagePreviewOnLeft = imagePreviewOnLeft,
        showFeedIcons = true,
        showFeedName = true,
        showSummary = true,
        shortenTitles = true,
        fontScale = ArticleListFontScale.MEDIUM,
        updateFeedIcons = {},
        updateFeedName = {},
        updateImagePreview = {},
        updateImagePreviewOnLeft = updateImagePreviewOnLeft,
        updateSummary = {},
        updateFontScale = {},
        updateShortenTitles = {},
    )
}
