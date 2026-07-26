package com.capyreader.app.ui.articles.detail

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.capyreader.app.ai.ArticleAiAction
import com.capyreader.app.ai.ArticleAiDisplayState
import com.capyreader.app.ui.theme.CapyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    application = android.app.Application::class,
    qualifiers = "w320dp-h900dp",
)
class ArticleAiSheetContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `primary actions dispatch exact non refresh payloads`() {
        val requests = mutableListOf<ActionRequest>()
        setContent(onRunAction = requests::add)

        composeRule.onNodeWithText("Summarize").performClick()
        composeRule.onNodeWithText("Key Points").performClick()
        composeRule.onNodeWithText("Translate").performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    ActionRequest(ArticleAiAction.SUMMARIZE, false, null),
                    ActionRequest(ArticleAiAction.KEY_POINTS, false, null),
                    ActionRequest(ArticleAiAction.TRANSLATE, false, null),
                ),
                requests,
            )
        }
    }

    @Test
    fun `question stays disabled for blank input and submits one normalized payload`() {
        val requests = mutableListOf<ActionRequest>()
        setContent(onRunAction = requests::add)
        val questionField = questionField()
        val askButton = composeRule.onNodeWithText("Ask")

        askButton.assertIsNotEnabled()
        questionField.performTextReplacement("   ")
        askButton.assertIsNotEnabled()
        questionField.performTextReplacement("  What matters most?  ")
        questionField.assertTextContains("  What matters most?  ")
        askButton
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    ActionRequest(
                        action = ArticleAiAction.QUESTION,
                        forceRefresh = false,
                        question = "What matters most?",
                    )
                ),
                requests,
            )
        }
    }

    @Test
    fun `matching completed results expose exact regenerate actions`() {
        val requests = mutableListOf<ActionRequest>()
        setContent(
            topState = state(
                action = ArticleAiAction.SUMMARIZE,
                result = "Summary",
            ),
            translationState = state(
                action = ArticleAiAction.TRANSLATE,
                result = "Translation",
            ),
            onRunAction = requests::add,
        )
        val regenerateButtons = composeRule.onAllNodesWithText("Regenerate")

        regenerateButtons.assertCountEquals(2)
        regenerateButtons[0].performClick()
        regenerateButtons[1].performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    ActionRequest(ArticleAiAction.SUMMARIZE, true, null),
                    ActionRequest(ArticleAiAction.TRANSLATE, true, null),
                ),
                requests,
            )
        }
    }

    @Test
    fun `blank and unrelated result states do not expose regenerate`() {
        setContent(
            topState = state(
                action = ArticleAiAction.SUMMARIZE,
                result = "   ",
            ),
            translationState = state(
                action = ArticleAiAction.KEY_POINTS,
                result = "Not a translation",
            ),
        )

        composeRule
            .onNodeWithText("Regenerate")
            .assertDoesNotExist()
    }

    @Test
    fun `matching loading actions are disabled and announced politely`() {
        setContent(
            topState = state(
                action = ArticleAiAction.SUMMARIZE,
                isLoading = true,
            ),
            translationState = state(
                action = ArticleAiAction.TRANSLATE,
                isLoading = true,
            ),
        )

        listOf("Summarize", "Translate").forEach { label ->
            composeRule
                .onNodeWithContentDescription(label)
                .assertIsNotEnabled()
                .assertLoadingSemantics()
        }
        composeRule.onNodeWithText("Key Points").assertIsEnabled()
        composeRule.onNodeWithText("Ask").assertIsNotEnabled()
    }

    @Test
    fun `question loading prevents duplicate submission and is announced politely`() {
        val requests = mutableListOf<ActionRequest>()
        setContent(
            topState = state(
                action = ArticleAiAction.QUESTION,
                isLoading = true,
            ),
            onRunAction = requests::add,
        )

        composeRule
            .onNodeWithContentDescription("Ask")
            .assertIsNotEnabled()
            .assertLoadingSemantics()
        composeRule.runOnIdle {
            assertTrue(requests.isEmpty())
        }
    }

    @Test
    fun `narrow completed layout stays bounded and follows visible keyboard order`() {
        setContent(
            topState = state(
                action = ArticleAiAction.SUMMARIZE,
                result = "Summary",
            ),
            modifier = Modifier.testTag(SHEET_CONTENT_TAG),
        )
        val summarize = composeRule.onNodeWithText("Summarize")
        val regenerate = composeRule.onNodeWithText("Regenerate")
        val keyPoints = composeRule.onNodeWithText("Key Points")
        val translate = composeRule.onNodeWithText("Translate")
        val question = questionField()
        val ask = composeRule.onNodeWithText("Ask")

        listOf(
            summarize to "Summarize",
            regenerate to "Regenerate",
            keyPoints to "Key Points",
            translate to "Translate",
        ).forEach { (interaction, label) ->
            interaction.assertTextEquals(label)
        }

        val contentBounds = composeRule
            .onNodeWithTag(SHEET_CONTENT_TAG)
            .fetchSemanticsNode()
            .boundsInRoot
        val summarizeBounds = summarize.fetchSemanticsNode().boundsInRoot
        val regenerateBounds = regenerate.fetchSemanticsNode().boundsInRoot

        assertTrue(summarizeBounds.left >= contentBounds.left)
        assertTrue(summarizeBounds.right <= regenerateBounds.left)
        assertTrue(regenerateBounds.right <= contentBounds.right)

        summarize
            .requestFocus()
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Tab) }
        regenerate
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Tab) }
        keyPoints
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Tab) }
        translate
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Tab) }
        question.assertIsFocused()
        question.performTextReplacement("Question")
        ask.assertIsEnabled()
        question.performKeyInput { pressKey(Key.Tab) }
        ask.assertIsFocused()
    }

    private fun setContent(
        topState: ArticleAiDisplayState? = null,
        translationState: ArticleAiDisplayState? = null,
        onRunAction: (ActionRequest) -> Unit = {},
        modifier: Modifier = Modifier,
    ) {
        composeRule.setContent {
            CapyTheme {
                ArticleAiSheetContent(
                    topState = topState,
                    translationState = translationState,
                    onRunAction = { action, forceRefresh, question ->
                        onRunAction(ActionRequest(action, forceRefresh, question))
                    },
                    modifier = modifier,
                )
            }
        }
    }

    private fun questionField() = composeRule.onNode(
        hasSetTextAction() and hasText("Ask a question about this article"),
    )

    private fun state(
        action: ArticleAiAction,
        isLoading: Boolean = false,
        result: String? = null,
    ) = ArticleAiDisplayState(
        action = action,
        isLoading = isLoading,
        result = result,
    )

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertLoadingSemantics() {
        assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "Working…",
            )
        )
        assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.LiveRegion,
                LiveRegionMode.Polite,
            )
        )
    }

    private data class ActionRequest(
        val action: ArticleAiAction,
        val forceRefresh: Boolean,
        val question: String?,
    )

    private companion object {
        const val SHEET_CONTENT_TAG = "article-ai-sheet-content"
    }
}
