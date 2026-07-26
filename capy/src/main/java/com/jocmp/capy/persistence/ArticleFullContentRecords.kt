package com.jocmp.capy.persistence

import com.jocmp.capy.common.TimeHelpers.nowUTC
import com.jocmp.capy.common.withIOContext
import com.jocmp.capy.db.Database

class ArticleFullContentRecords(
    private val database: Database,
) {
    suspend fun find(articleID: String): ArticleFullContentRecord? = withIOContext {
        database.articleFullContentCacheQueries.findByArticleID(
            articleID = articleID,
            mapper = { id, contentHTML, createdAt, updatedAt ->
                ArticleFullContentRecord(
                    articleID = id,
                    contentHTML = contentHTML,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                )
            }
        ).executeAsOneOrNull()
    }

    suspend fun upsert(articleID: String, contentHTML: String): Unit = withIOContext {
        val now = nowUTC().toEpochSecond()

        database.articleFullContentCacheQueries.upsert(
            articleID = articleID,
            contentHTML = contentHTML,
            createdAt = now,
            updatedAt = now,
        )
        Unit
    }

    suspend fun deleteOrphans(): Unit = withIOContext {
        database.articleFullContentCacheQueries.deleteWithoutArticle()
        Unit
    }
}

data class ArticleFullContentRecord(
    val articleID: String,
    val contentHTML: String,
    val createdAt: Long,
    val updatedAt: Long,
)
