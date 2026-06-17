package com.jocmp.capy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArticleAutomationRuleMatcherTest {
    @Test
    fun testAgainstReturnsActionsForMatchingRule() {
        val rule = ArticleAutomationRule(
            matchMode = RuleMatchMode.ALL,
            conditions = listOf(
                ArticleRuleCondition(
                    field = ArticleRuleField.TITLE,
                    operator = ArticleRuleOperator.CONTAINS,
                    value = "android",
                ),
                ArticleRuleCondition(
                    field = ArticleRuleField.AUTHOR,
                    operator = ArticleRuleOperator.EQUALS,
                    value = "Ada",
                ),
            ),
            actions = setOf(ArticleRuleAction.STAR, ArticleRuleAction.NOTIFY),
        )

        val result = rule.testAgainst(sampleArticle(title = "Android update", author = "Ada"))

        assertTrue(result.matched)
        assertEquals(setOf(ArticleRuleAction.STAR, ArticleRuleAction.NOTIFY), result.actions)
    }

    @Test
    fun testAgainstSupportsAnyRegexCondition() {
        val rule = ArticleAutomationRule(
            matchMode = RuleMatchMode.ANY,
            conditions = listOf(
                ArticleRuleCondition(
                    field = ArticleRuleField.TITLE,
                    operator = ArticleRuleOperator.REGEX,
                    value = "/CVE-\\d+/",
                ),
                ArticleRuleCondition(
                    field = ArticleRuleField.CONTENT,
                    operator = ArticleRuleOperator.CONTAINS,
                    value = "security patch",
                ),
            ),
            actions = setOf(ArticleRuleAction.MARK_READ),
        )

        assertTrue(rule.testAgainst(sampleArticle(title = "CVE-2026 advisory")).matched)
        assertFalse(rule.testAgainst(sampleArticle(title = "Weekly release")).matched)
    }

    private fun sampleArticle(
        title: String = "",
        author: String = "",
        content: String = "",
    ) = ArticleAutomationArticle(
        title = title,
        author = author,
        summary = content,
        contentHTML = content,
        feedTitle = "",
        feedURL = "",
    )
}
