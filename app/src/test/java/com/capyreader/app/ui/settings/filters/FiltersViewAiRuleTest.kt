package com.capyreader.app.ui.settings.filters

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import com.capyreader.app.ai.ArticleAiRuleRunAvailability
import com.capyreader.app.ui.theme.CapyTheme
import com.jocmp.capy.ArticleAutomationRule
import com.jocmp.capy.ArticleRuleAction
import com.jocmp.capy.ArticleRuleCondition
import com.jocmp.capy.ArticleRuleField
import com.jocmp.capy.ArticleRuleOperator
import com.jocmp.capy.RuleMatchMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class FiltersViewAiRuleTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `AI rule creation requires bounded criterion and saves trimmed value`() {
        var addedRule: ArticleAutomationRule? = null
        setFilters(onAddRule = { addedRule = it })

        composeRule
            .onNodeWithText(AI_NOTICE)
            .assertDoesNotExist()
        composeRule
            .onNodeWithText("Test rule against sample text")
            .assertExists()

        aiModeSwitch().performScrollTo().performClick()

        composeRule
            .onNodeWithText(AI_NOTICE)
            .assertExists()
        composeRule
            .onNodeWithText("Test rule against sample text")
            .assertDoesNotExist()

        saveButton("Add rule").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertNull(addedRule)
        }

        val criterionField = composeRule.onNode(
            hasSetTextAction() and hasText("Describe which articles should match")
        )
        val oversizedCriterion = "x".repeat(600)
        criterionField.performTextReplacement(oversizedCriterion)
        composeRule
            .onNode(hasSetTextAction() and hasText("x".repeat(500)))
            .assertTextEquals("x".repeat(500))

        composeRule
            .onNode(hasSetTextAction() and hasText("x".repeat(500)))
            .performTextReplacement("  Important engineering releases  ")
        saveButton("Add rule").performScrollTo().performClick()

        composeRule.runOnIdle {
            val rule = checkNotNull(addedRule)
            assertTrue(rule.aiEnabled)
            assertEquals("Important engineering releases", rule.aiCriterion)
            assertEquals(ArticleRuleField.ANY, rule.field)
            assertEquals("", rule.pattern)
            assertTrue(rule.conditions.isEmpty())
            assertEquals(setOf(ArticleRuleAction.MUTE), rule.actions)
        }
    }

    @Test
    fun `editing across modes preserves deterministic conditions`() {
        val conditions = listOf(
            ArticleRuleCondition(
                field = ArticleRuleField.TITLE,
                operator = ArticleRuleOperator.CONTAINS,
                value = "Android",
            ),
            ArticleRuleCondition(
                field = ArticleRuleField.AUTHOR,
                operator = ArticleRuleOperator.EQUALS,
                value = "Ada",
            ),
        )
        val existingRule = ArticleAutomationRule(
            id = "existing-rule",
            name = "Existing rule",
            field = ArticleRuleField.TITLE,
            pattern = "Android",
            matchMode = RuleMatchMode.ANY,
            conditions = conditions,
            actions = setOf(ArticleRuleAction.STAR),
        )
        var updatedRule: ArticleAutomationRule? = null
        setFilters(
            rules = listOf(existingRule),
            onUpdateRule = { updatedRule = it },
        )

        composeRule
            .onNodeWithText("Existing rule", useUnmergedTree = true)
            .performScrollTo()
            .performTouchInput { click() }
        formAiModeSwitch().assertIsOff().performScrollTo().performClick()
        formAiModeSwitch().assertIsOn()
        composeRule
            .onNode(
                hasSetTextAction() and
                    hasText("Describe which articles should match")
            )
            .performTextReplacement("  Important updates  ")

        formAiModeSwitch().performScrollTo().performClick()
        formAiModeSwitch().assertIsOff()

        composeRule
            .onNode(hasSetTextAction() and hasText("Android"))
            .assertExists()
        composeRule
            .onNode(hasSetTextAction() and hasText("Ada"))
            .assertExists()
        composeRule
            .onNodeWithText("Test rule against sample text")
            .assertExists()

        formAiModeSwitch().performScrollTo().performClick()
        saveButton("Save rule").performScrollTo().performClick()

        composeRule.runOnIdle {
            val rule = checkNotNull(updatedRule)
            assertEquals(existingRule.id, rule.id)
            assertEquals(RuleMatchMode.ANY, rule.matchMode)
            assertEquals(conditions, rule.conditions)
            assertEquals(ArticleRuleField.TITLE, rule.field)
            assertEquals("Android", rule.pattern)
            assertEquals(setOf(ArticleRuleAction.STAR), rule.actions)
            assertTrue(rule.aiEnabled)
            assertEquals("Important updates", rule.aiCriterion)
        }
    }

    @Test
    fun `full view status follows disable delete and one run action`() {
        val aiRule = ArticleAutomationRule(
            id = "ai-rule",
            name = "Priority rule",
            enabled = true,
            actions = setOf(ArticleRuleAction.STAR),
            aiEnabled = true,
            aiCriterion = "Priority releases",
        )
        var runCount = 0

        composeRule.setContent {
            var rules by remember { mutableStateOf(listOf(aiRule)) }
            val availability = if (rules.any { it.enabled }) {
                ArticleAiRuleRunAvailability.READY
            } else {
                ArticleAiRuleRunAvailability.NO_ENABLED_AI_RULES
            }

            CapyTheme {
                FiltersView(
                    onAddKeyword = {},
                    onRemoveKeyword = {},
                    keywords = emptyList(),
                    onAddRule = {},
                    onUpdateRule = { updated ->
                        rules = rules.map { rule ->
                            if (rule.id == updated.id) updated else rule
                        }
                    },
                    onRemoveRule = { removed ->
                        rules = rules.filterNot { it.id == removed.id }
                    },
                    onMoveRule = { _, _ -> },
                    rules = rules,
                    aiRuleRunAvailability = availability,
                    onRunAiRules = { runCount += 1 },
                )
            }
        }

        composeRule
            .onNodeWithText("AI rule activity")
            .assertExists()
        composeRule
            .onNodeWithText("Run now")
            .performScrollTo()
            .performClick()
        composeRule.runOnIdle {
            assertEquals(1, runCount)
        }

        ruleEnabledSwitch().performScrollTo().performClick()

        composeRule
            .onNodeWithText(
                "Enable at least one AI rule to run it.",
                substring = true,
            )
            .assertExists()
        composeRule
            .onNodeWithText("Run now")
            .assertDoesNotExist()

        composeRule
            .onNodeWithContentDescription("Remove rule")
            .performScrollTo()
            .performClick()

        composeRule
            .onNodeWithText("AI rule activity")
            .assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(1, runCount)
        }
    }

    private fun setFilters(
        rules: List<ArticleAutomationRule> = emptyList(),
        onAddRule: (ArticleAutomationRule) -> Unit = {},
        onUpdateRule: (ArticleAutomationRule) -> Unit = {},
    ) {
        composeRule.setContent {
            CapyTheme {
                FiltersView(
                    onAddKeyword = {},
                    onRemoveKeyword = {},
                    keywords = emptyList(),
                    onAddRule = onAddRule,
                    onUpdateRule = onUpdateRule,
                    onRemoveRule = {},
                    onMoveRule = { _, _ -> },
                    rules = rules,
                )
            }
        }
    }

    private fun aiModeSwitch() = composeRule
        .onNodeWithContentDescription("AI-assisted evaluation")

    private fun formAiModeSwitch() = aiModeSwitch()

    private fun ruleEnabledSwitch() = composeRule
        .onAllNodes(isToggleable())[0]

    private fun saveButton(label: String) = composeRule
        .onNodeWithContentDescription(label)

    private companion object {
        const val AI_NOTICE =
            "Sends only bounded title, feed, author, and summary metadata to " +
                "your configured AI provider. Provider charges may apply. " +
                "Limited to 10 requests per refresh and 20 per day."
    }
}
