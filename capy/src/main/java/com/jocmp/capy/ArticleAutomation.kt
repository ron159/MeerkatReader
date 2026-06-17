package com.jocmp.capy

import com.jocmp.capy.common.TimeHelpers
import com.jocmp.capy.db.Database
import com.jocmp.capy.persistence.ArticleRuleMatchRecords
import com.jocmp.capy.persistence.SavedSearchRecords
import java.time.ZonedDateTime

class ArticleAutomation(
    private val database: Database,
    private val preferences: AccountPreferences,
) {
    private val savedSearchRecords = SavedSearchRecords(database)
    private val ruleMatchRecords = ArticleRuleMatchRecords(database)

    fun evaluate(article: ArticleAutomationArticle): ArticleAutomationResult {
        val legacyMuted = preferences.filterKeywords.get().any { keyword ->
            matchesPattern(article.title.orEmpty(), keyword) ||
                    matchesPattern(article.summary.orEmpty(), keyword) ||
                    matchesPattern(article.contentHTML.orEmpty(), keyword)
        }

        return preferences.automationRules.get()
            .filter { it.enabled && it.automationConditions().isNotEmpty() && it.actions.isNotEmpty() }
            .filter { it.matchesAutomationArticle(article) }
            .fold(ArticleAutomationResult(mute = legacyMuted)) { result, rule ->
                val keep = ArticleRuleAction.KEEP in rule.actions

                result.copy(
                    mute = (result.mute || ArticleRuleAction.MUTE in rule.actions) && !keep,
                    markRead = result.markRead || ArticleRuleAction.MARK_READ in rule.actions,
                    star = result.star || ArticleRuleAction.STAR in rule.actions,
                    categoryName = result.categoryName ?: rule.categoryName(),
                    notify = result.notify || ArticleRuleAction.NOTIFY in rule.actions,
                    matches = result.matches + rule.toMatch(),
                )
            }
    }

    fun applyLocalActions(
        articleID: String,
        result: ArticleAutomationResult,
        updatedAt: ZonedDateTime = TimeHelpers.nowUTC(),
    ) {
        if (result.markRead) {
            database.articlesQueries.markRead(
                articleIDs = listOf(articleID),
                read = true,
                lastReadAt = updatedAt.toEpochSecond(),
            )
        }

        if (result.star) {
            database.articlesQueries.markStarred(
                articleID = articleID,
                starred = true,
                lastUnstarredAt = null,
            )
        }

        result.categoryName?.let { categoryName ->
            val categoryID = SavedSearchRecords.automationID(categoryName)
            savedSearchRecords.upsert(
                id = categoryID,
                name = categoryName,
            )
            savedSearchRecords.upsertArticle(
                articleID = articleID,
                savedSearchID = categoryID,
            )
        }

        if (result.notify) {
            database.article_notificationsQueries.createNotification(article_id = articleID)
        }

        logMatches(
            articleID = articleID,
            result = result,
            matchedAt = updatedAt,
        )
    }

    fun logMatches(
        articleID: String,
        result: ArticleAutomationResult,
        matchedAt: ZonedDateTime = TimeHelpers.nowUTC(),
    ) {
        if (result.matches.isEmpty()) {
            return
        }

        ruleMatchRecords.insert(
            articleID = articleID,
            matches = result.matches,
            matchedAt = matchedAt,
        )
    }

    fun clearMutedArticle(articleID: String) {
        database.articlesQueries.deletePageByID(articleID)
    }

    private fun ArticleAutomationRule.categoryName(): String? {
        if (ArticleRuleAction.CATEGORIZE !in actions) {
            return null
        }

        return categoryName.trim().ifBlank { name.trim() }.ifBlank { null }
    }

    private fun ArticleAutomationRule.toMatch(): ArticleAutomationMatch {
        val activeConditions = automationConditions()
        val displayName = name.trim().ifBlank { pattern.trim() }.ifBlank { id }
        val explanation = if (activeConditions.size == 1) {
            val condition = activeConditions.single()
            "Matched ${condition.field.name.lowercase()} rule \"$displayName\" with ${condition.operator.name.lowercase()} \"${condition.value.trim()}\"."
        } else {
            "Matched ${matchMode.name.lowercase()} rule \"$displayName\" with ${activeConditions.size} conditions."
        }

        return ArticleAutomationMatch(
            ruleID = id,
            ruleName = displayName,
            actions = actions,
            explanation = explanation,
        )
    }

    private fun matchesPattern(value: String, pattern: String): Boolean {
        val query = pattern.trim()

        if (query.isEmpty()) {
            return false
        }

        return if (query.length > 2 && query.startsWith("/") && query.endsWith("/")) {
            runCatching {
                Regex(query.substring(1, query.lastIndex), RegexOption.IGNORE_CASE)
                    .containsMatchIn(value)
            }.getOrDefault(false)
        } else {
            value.contains(query, ignoreCase = true)
        }
    }

}

data class ArticleAutomationArticle(
    val title: String?,
    val author: String?,
    val summary: String?,
    val contentHTML: String?,
    val feedTitle: String,
    val feedURL: String,
)

data class ArticleAutomationResult(
    val mute: Boolean = false,
    val markRead: Boolean = false,
    val star: Boolean = false,
    val categoryName: String? = null,
    val notify: Boolean = false,
    val matches: List<ArticleAutomationMatch> = emptyList(),
) {
    val shouldMarkReadRemotely: Boolean
        get() = mute || markRead
}

data class ArticleAutomationMatch(
    val ruleID: String,
    val ruleName: String,
    val actions: Set<ArticleRuleAction>,
    val explanation: String,
)
