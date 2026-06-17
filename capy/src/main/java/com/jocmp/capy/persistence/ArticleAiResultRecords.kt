package com.jocmp.capy.persistence

import com.jocmp.capy.common.TimeHelpers.nowUTC
import com.jocmp.capy.common.withIOContext
import com.jocmp.capy.db.Database

class ArticleAiResultRecords(
    private val database: Database,
) {
    suspend fun find(input: ArticleAiResultInput): ArticleAiResultRecord? = withIOContext {
        database.articleAiResultsQueries.findResult(
            articleID = input.articleID,
            action = input.action,
            provider = input.provider,
            baseURL = input.baseURL,
            model = input.model,
            language = input.language,
            promptHash = input.promptHash,
            contentHash = input.contentHash,
            mapper = { id, articleID, action, provider, baseURL, model, language, promptHash, contentHash, resultText, createdAt, updatedAt ->
                ArticleAiResultRecord(
                    id = id,
                    articleID = articleID,
                    action = action,
                    provider = provider,
                    baseURL = baseURL,
                    model = model,
                    language = language,
                    promptHash = promptHash,
                    contentHash = contentHash,
                    resultText = resultText,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                )
            },
        ).executeAsOneOrNull()
    }

    suspend fun upsert(input: ArticleAiResultInput, resultText: String) = withIOContext {
        val now = nowUTC().toEpochSecond()

        database.articleAiResultsQueries.upsert(
            id = input.id,
            articleID = input.articleID,
            action = input.action,
            provider = input.provider,
            baseURL = input.baseURL,
            model = input.model,
            language = input.language,
            promptHash = input.promptHash,
            contentHash = input.contentHash,
            resultText = resultText,
            createdAt = now,
            updatedAt = now,
        )
    }

    suspend fun deleteAll() = withIOContext {
        database.articleAiResultsQueries.deleteAll()
    }

    suspend fun deleteOrphans() = withIOContext {
        database.articleAiResultsQueries.deleteWithoutArticle()
    }
}

data class ArticleAiResultInput(
    val id: String,
    val articleID: String,
    val action: String,
    val provider: String,
    val baseURL: String,
    val model: String,
    val language: String,
    val promptHash: String,
    val contentHash: String,
)

data class ArticleAiResultRecord(
    val id: String,
    val articleID: String,
    val action: String,
    val provider: String,
    val baseURL: String,
    val model: String,
    val language: String,
    val promptHash: String,
    val contentHash: String,
    val resultText: String,
    val createdAt: Long,
    val updatedAt: Long,
)
