package com.capyreader.app.ui.articles.list

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.capyreader.app.ui.theme.CapyTheme
import com.jocmp.capy.Article
import com.jocmp.capy.ArticleIntegrationExportState
import com.jocmp.capy.persistence.ArticleIntegrationExportRecord
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.URL
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(
    application = android.app.Application::class,
    qualifiers = "w411dp-h891dp",
)
class ArticleActionMenuWallabagTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `Wallabag action is hidden when disabled or article has no URL`() {
        val showWallabag = mutableStateOf(false)
        val currentArticle = mutableStateOf(article())
        composeRule.setContent {
            CapyTheme {
                ArticleActionMenu(
                    expanded = true,
                    article = currentArticle.value,
                    index = 0,
                    showWallabag = showWallabag.value,
                )
            }
        }

        composeRule
            .onNodeWithText("Save to Wallabag")
            .assertDoesNotExist()

        composeRule.runOnIdle {
            showWallabag.value = true
            currentArticle.value = article(url = null)
        }

        composeRule
            .onNodeWithText("Save to Wallabag")
            .assertDoesNotExist()
    }

    @Test
    fun `initial save dismisses and exports exactly once`() {
        var dismissCount = 0
        var exportCount = 0
        setMenuItem(
            record = null,
            onDismiss = { dismissCount += 1 },
            onExport = { exportCount += 1 },
        )

        composeRule
            .onNodeWithText("Save to Wallabag")
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, dismissCount)
            assertEquals(1, exportCount)
        }
    }

    @Test
    fun `queued exporting and exported states remain disabled`() {
        val currentRecord = mutableStateOf(
            record(ArticleIntegrationExportState.QUEUED)
        )
        composeRule.setContent {
            CapyTheme {
                WallabagMenuItem(
                    onDismissRequest = {},
                    record = currentRecord.value,
                    onExportWallabag = {},
                )
            }
        }

        composeRule
            .onNodeWithText("Wallabag export queued")
            .assertIsNotEnabled()

        composeRule.runOnIdle {
            currentRecord.value = record(ArticleIntegrationExportState.EXPORTING)
        }
        composeRule
            .onNodeWithText("Wallabag export queued")
            .assertIsNotEnabled()

        composeRule.runOnIdle {
            currentRecord.value = record(ArticleIntegrationExportState.EXPORTED)
        }
        composeRule
            .onNodeWithText("Saved to Wallabag")
            .assertIsNotEnabled()
    }

    @Test
    fun `failed state exposes informational detail and one retry action`() {
        var dismissCount = 0
        var exportCount = 0
        val failedRecord = record(
            state = ArticleIntegrationExportState.FAILED,
            errorMessage = "Wallabag authentication failed",
        )
        composeRule.setContent {
            CapyTheme {
                Column {
                    WallabagFailureMenuItem(failedRecord)
                    WallabagMenuItem(
                        onDismissRequest = { dismissCount += 1 },
                        record = failedRecord,
                        onExportWallabag = { exportCount += 1 },
                    )
                }
            }
        }

        composeRule
            .onNodeWithContentDescription("Wallabag authentication failed")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        composeRule
            .onNodeWithText("Retry Wallabag export")
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, dismissCount)
            assertEquals(1, exportCount)
        }
    }

    @Test
    fun `blank failed record shows localized fallback in full menu`() {
        composeRule.setContent {
            CapyTheme {
                ArticleActionMenu(
                    expanded = true,
                    article = article(),
                    index = 0,
                    showWallabag = true,
                    wallabagExportRecord = record(
                        state = ArticleIntegrationExportState.FAILED,
                        errorMessage = "",
                    ),
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("Wallabag export failed")
            .performScrollTo()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        composeRule
            .onNodeWithText("Retry Wallabag export")
            .performScrollTo()
            .assertIsEnabled()
    }

    @Test
    fun `unsafe historical failure text falls back without entering semantics`() {
        val unsafeMessage = mutableStateOf("")
        composeRule.setContent {
            CapyTheme {
                WallabagFailureMenuItem(
                    record(
                        state = ArticleIntegrationExportState.FAILED,
                        errorMessage = unsafeMessage.value,
                    )
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("Wallabag export failed")
            .assertExists()

        composeRule.runOnIdle {
            unsafeMessage.value =
                "Bearer server-secret raw-response-payload"
        }

        composeRule
            .onNodeWithContentDescription("Wallabag export failed")
            .assertExists()
        composeRule
            .onNodeWithText("server-secret", substring = true)
            .assertDoesNotExist()
        composeRule
            .onNodeWithContentDescription(
                "Bearer server-secret raw-response-payload"
            )
            .assertDoesNotExist()
    }

    private fun setMenuItem(
        record: ArticleIntegrationExportRecord?,
        onDismiss: () -> Unit,
        onExport: () -> Unit,
    ) {
        composeRule.setContent {
            CapyTheme {
                WallabagMenuItem(
                    onDismissRequest = onDismiss,
                    record = record,
                    onExportWallabag = onExport,
                )
            }
        }
    }

    private fun record(
        state: ArticleIntegrationExportState,
        errorMessage: String? = null,
    ) = ArticleIntegrationExportRecord(
        id = "record",
        articleID = "article",
        integrationID = "wallabag",
        state = state,
        remoteID = null,
        errorMessage = errorMessage,
        updatedAt = 0L,
    )

    private fun article(url: URL? = URL("https://example.com/article")) = Article(
        id = "article",
        feedID = "feed",
        title = "Example article",
        author = null,
        contentHTML = "",
        url = url,
        summary = "",
        imageURL = null,
        updatedAt = ZonedDateTime.parse("2026-07-25T00:00:00Z"),
        publishedAt = ZonedDateTime.parse("2026-07-25T00:00:00Z"),
        read = false,
        starred = false,
    )
}
