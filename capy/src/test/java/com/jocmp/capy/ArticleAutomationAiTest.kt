package com.jocmp.capy

import com.jocmp.capy.fixtures.AccountFixture
import com.jocmp.capy.fixtures.ArticleFixture
import com.jocmp.capy.persistence.ArticleRuleMatchRecords
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArticleAutomationAiTest {
    @JvmField
    @Rule
    val folder = TemporaryFolder()

    @Test
    fun `serialized rules keep AI evaluation off by default`() {
        val rule = Json.decodeFromString<ArticleAutomationRule>(
            """{"id":"legacy","name":"Legacy","pattern":"Kotlin"}"""
        )

        assertFalse(rule.aiEnabled)
        assertEquals("", rule.aiCriterion)
    }

    @Test
    fun `deterministic evaluation skips AI rules without changing normal rules`() = runTest {
        val account = AccountFixture.create(parentFolder = folder)
        account.preferences.automationRules.set(
            listOf(
                ArticleAutomationRule(
                    id = "deterministic",
                    pattern = "Kotlin",
                    actions = setOf(ArticleRuleAction.STAR),
                ),
                ArticleAutomationRule(
                    id = "ai",
                    pattern = "Kotlin",
                    actions = setOf(ArticleRuleAction.MUTE),
                    aiEnabled = true,
                    aiCriterion = "Important engineering news",
                ),
            )
        )
        val automation = ArticleAutomation(account.database, account.preferences)

        val result = automation.evaluate(
            ArticleAutomationArticle(
                title = "Kotlin update",
                author = null,
                summary = null,
                contentHTML = null,
                feedTitle = "Engineering",
                feedURL = "https://example.com/feed",
            )
        )

        assertTrue(result.star)
        assertFalse(result.mute)
        assertEquals(listOf("deterministic"), result.matches.map { it.ruleID })
    }

    @Test
    fun `AI match explanation is persisted in existing rule history`() = runTest {
        val account = AccountFixture.create(parentFolder = folder)
        val article = ArticleFixture(account.database).create(read = false)
        val automation = ArticleAutomation(account.database, account.preferences)
        val rule = ArticleAutomationRule(
            id = "ai-rule",
            name = "Priority",
            actions = setOf(ArticleRuleAction.STAR),
            aiEnabled = true,
            aiCriterion = "High-priority project updates",
        )
        val explanation = "The summary describes a blocking project decision."

        automation.applyLocalActions(
            articleID = article.id,
            result = automation.resultForAiMatch(rule, explanation),
        )

        val match = ArticleRuleMatchRecords(account.database)
            .findByArticleID(article.id)
            .single()
        assertEquals("ai-rule", match.ruleID)
        assertEquals(explanation, match.explanation)
    }
}
