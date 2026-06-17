package com.jocmp.capy.persistence

import com.jocmp.capy.common.TimeHelpers.nowUTC
import com.jocmp.capy.common.withIOContext
import com.jocmp.capy.db.Database

class ArticleAiDigestRecords(
    private val database: Database,
) {
    suspend fun find(id: String): ArticleAiDigestRecord? = withIOContext {
        database.aiDigestsQueries.find(
            id = id,
            mapper = ::mapper,
        ).executeAsOneOrNull()
    }

    suspend fun upsert(input: ArticleAiDigestInput, resultText: String) = withIOContext {
        database.aiDigestsQueries.upsert(
            id = input.id,
            filterJson = input.filterJson,
            provider = input.provider,
            model = input.model,
            language = input.language,
            articleIdsJson = input.articleIdsJson,
            resultText = resultText,
            createdAt = nowUTC().toEpochSecond(),
        )
    }

    suspend fun deleteAll() = withIOContext {
        database.aiDigestsQueries.deleteAll()
    }

    private fun mapper(
        id: String,
        filterJson: String,
        provider: String,
        model: String,
        language: String,
        articleIdsJson: String,
        resultText: String,
        createdAt: Long,
    ) = ArticleAiDigestRecord(
        id = id,
        filterJson = filterJson,
        provider = provider,
        model = model,
        language = language,
        articleIdsJson = articleIdsJson,
        resultText = resultText,
        createdAt = createdAt,
    )
}

data class ArticleAiDigestInput(
    val id: String,
    val filterJson: String,
    val provider: String,
    val model: String,
    val language: String,
    val articleIdsJson: String,
)

data class ArticleAiDigestRecord(
    val id: String,
    val filterJson: String,
    val provider: String,
    val model: String,
    val language: String,
    val articleIdsJson: String,
    val resultText: String,
    val createdAt: Long,
)
