package com.jocmp.capy.persistence

import com.jocmp.capy.ArticleAutomationMatch
import com.jocmp.capy.RandomUUID
import com.jocmp.capy.common.TimeHelpers.nowUTC
import com.jocmp.capy.common.withIOContext
import com.jocmp.capy.db.Database
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.ZonedDateTime

class ArticleRuleMatchRecords(
    private val database: Database,
) {
    private val json = Json

    fun findByArticleID(articleID: String): List<ArticleRuleMatchRecord> {
        return database.articleRuleMatchesQueries.findByArticleID(
            articleID = articleID,
            mapper = { id, matchArticleID, ruleID, ruleName, actionsJSON, matchedAt, explanation ->
                ArticleRuleMatchRecord(
                    id = id,
                    articleID = matchArticleID,
                    ruleID = ruleID,
                    ruleName = ruleName,
                    actionsJSON = actionsJSON,
                    matchedAt = matchedAt,
                    explanation = explanation,
                )
            }
        ).executeAsList()
    }

    fun insert(
        articleID: String,
        matches: List<ArticleAutomationMatch>,
        matchedAt: ZonedDateTime = nowUTC(),
    ) {
        matches.forEach { match ->
            database.articleRuleMatchesQueries.insert(
                id = RandomUUID.generate(),
                articleID = articleID,
                ruleID = match.ruleID,
                ruleName = match.ruleName,
                actionsJSON = json.encodeToString(match.actions.map { it.name }),
                matchedAt = matchedAt.toEpochSecond(),
                explanation = match.explanation,
            )
        }
    }

    suspend fun deleteByArticleID(articleID: String) = withIOContext {
        database.articleRuleMatchesQueries.deleteByArticleID(articleID)
    }

    suspend fun deleteAll() = withIOContext {
        database.articleRuleMatchesQueries.deleteAll()
    }
}

data class ArticleRuleMatchRecord(
    val id: String,
    val articleID: String,
    val ruleID: String,
    val ruleName: String,
    val actionsJSON: String,
    val matchedAt: Long,
    val explanation: String,
)
