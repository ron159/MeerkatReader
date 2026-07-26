package com.capyreader.app.ui.articles.list

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import com.capyreader.app.ui.theme.CapyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    application = android.app.Application::class,
    qualifiers = "w1200dp-h800dp",
)
class SearchQualifierChipsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `supported qualifiers render in deterministic non interactive order`() {
        val labels = listOf(
            "Status: Saved",
            "Feed: F",
            "Folder: G",
            "Author: A",
            "Title: T",
            "After: 2026-01-01",
            "Before: 2026-01-31",
            "Has image",
            "Has audio",
        )
        setChips(
            query = "is:saved feed:F folder:G author:A title:T " +
                "after:2026-01-01 before:2026-01-31 has:image has:audio",
        )

        val leftPositions = labels.map { label ->
            composeRule
                .onNodeWithText(label)
                .assertExists()
                .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
                .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
                .fetchSemanticsNode()
                .boundsInRoot
                .left
        }

        assertEquals(leftPositions.sorted(), leftPositions)
    }

    @Test
    fun `all supported statuses use localized labels`() {
        composeRule.setContent {
            CapyTheme {
                Column {
                    SearchQualifierChips("is:read")
                    SearchQualifierChips("is:unread")
                    SearchQualifierChips("is:starred")
                    SearchQualifierChips("is:saved")
                }
            }
        }

        listOf(
            "Status: Read",
            "Status: Unread",
            "Status: Starred",
            "Status: Saved",
        ).forEach { label ->
            composeRule.onNodeWithText(label).assertExists()
        }
    }

    @Test
    fun `quoted multi word qualifiers render complete labels`() {
        setChips(
            query = "feed:\"Android Weekly\" folder:\"Mobile News\" " +
                "author:\"Alice Smith\" title:\"Security Update\"",
        )

        listOf(
            "Feed: Android Weekly",
            "Folder: Mobile News",
            "Author: Alice Smith",
            "Title: Security Update",
        ).forEach { label ->
            composeRule.onNodeWithText(label).assertExists()
        }
    }

    @Test
    fun `blank plain and unknown searches do not render qualifier rows`() {
        composeRule.setContent {
            CapyTheme {
                Column {
                    SearchQualifierChips(
                        query = "",
                        modifier = Modifier.testTag("blank"),
                    )
                    SearchQualifierChips(
                        query = "android security update",
                        modifier = Modifier.testTag("plain"),
                    )
                    SearchQualifierChips(
                        query = "site:example after:not-a-date has:video",
                        modifier = Modifier.testTag("unknown"),
                    )
                }
            }
        }

        listOf("blank", "plain", "unknown").forEach { tag ->
            composeRule.onNodeWithTag(tag).assertDoesNotExist()
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h800dp")
    fun `long combined qualifiers keep full text and scroll horizontally`() {
        val feedName = "ThisIsAVeryLongFeedNameThatMustRemainComplete"
        setChips(
            query = "feed:$feedName has:image has:audio",
        )

        composeRule
            .onNodeWithText("Feed: $feedName")
            .assertExists()
        composeRule
            .onNodeWithTag(QUALIFIER_ROW_TAG)
            .performScrollToNode(hasText("Has audio"))
        composeRule
            .onNodeWithText("Has audio")
            .assertExists()
    }

    private fun setChips(query: String) {
        composeRule.setContent {
            CapyTheme {
                SearchQualifierChips(
                    query = query,
                    modifier = Modifier.testTag(QUALIFIER_ROW_TAG),
                )
            }
        }
    }

    private companion object {
        const val QUALIFIER_ROW_TAG = "qualifier-row"
    }
}
