package com.jocmp.capy.accounts

import com.jocmp.capy.ArticleAutomationRule
import com.jocmp.capy.ArticleRuleAction
import com.jocmp.capy.ArticleRuleCondition
import com.jocmp.capy.ArticleRuleField
import com.jocmp.capy.ArticleRuleOperator
import com.jocmp.capy.db.Database
import com.jocmp.capy.persistence.ArticleRecords
import com.jocmp.capy.persistence.ArticleRuleMatchRecords
import com.jocmp.capy.persistence.SavedSearchRecords
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal const val AUTOMATION_CATEGORY = "Automation test"

internal fun automationTestRule(titleText: String) = ArticleAutomationRule(
    id = "automation-test-rule",
    name = "Automation test rule",
    categoryName = AUTOMATION_CATEGORY,
    actions = setOf(
        ArticleRuleAction.MARK_READ,
        ArticleRuleAction.STAR,
        ArticleRuleAction.CATEGORIZE,
        ArticleRuleAction.NOTIFY,
    ),
    conditions = listOf(
        ArticleRuleCondition(
            field = ArticleRuleField.TITLE,
            operator = ArticleRuleOperator.CONTAINS,
            value = titleText,
        )
    ),
)

internal suspend fun assertAutomationApplied(
    database: Database,
    articleID: String,
) {
    val article = assertNotNull(ArticleRecords(database).find(articleID))
    assertTrue(article.read)
    assertTrue(article.starred)

    val savedSearchRecords = SavedSearchRecords(database)
    val categoryID = SavedSearchRecords.automationID(AUTOMATION_CATEGORY)
    assertEquals(listOf(articleID), savedSearchRecords.articleIDs(categoryID))

    val matches = ArticleRuleMatchRecords(database).findByArticleID(articleID)
    assertEquals(1, matches.size)
    assertEquals("automation-test-rule", matches.single().ruleID)

    assertEquals(
        1L,
        database.article_notificationsQueries.countActive().executeAsOne(),
    )
}
