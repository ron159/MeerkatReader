package com.jocmp.capy.persistence

import com.jocmp.capy.common.TimeHelpers.nowUTC
import com.jocmp.capy.common.withIOContext
import com.jocmp.capy.db.Database
import java.time.ZonedDateTime

class ArticleReadingProgressRecords(
    private val database: Database,
) {
    suspend fun find(articleID: String): ArticleReadingProgressRecord? = withIOContext {
        database.articleReadingProgressQueries.find(
            articleID = articleID,
            mapper = ::mapper,
        ).executeAsOneOrNull()
    }

    suspend fun upsert(
        articleID: String,
        scrollPercent: Double,
        updatedAt: ZonedDateTime = nowUTC(),
    ): Unit = withIOContext {
        database.articleReadingProgressQueries.upsert(
            articleID = articleID,
            scrollPercent = scrollPercent.coerceIn(0.0, 1.0),
            updatedAt = updatedAt.toEpochSecond(),
        )
        Unit
    }

    suspend fun delete(articleID: String): Unit = withIOContext {
        database.articleReadingProgressQueries.delete(articleID)
        Unit
    }

    suspend fun deleteAll(): Unit = withIOContext {
        database.articleReadingProgressQueries.deleteAll()
        Unit
    }

    suspend fun deleteOrphans(): Unit = withIOContext {
        database.articleReadingProgressQueries.deleteWithoutArticle()
        Unit
    }

    private fun mapper(
        articleID: String,
        scrollPercent: Double,
        updatedAt: Long,
    ) = ArticleReadingProgressRecord(
        articleID = articleID,
        scrollPercent = scrollPercent,
        updatedAt = updatedAt,
    )
}

data class ArticleReadingProgressRecord(
    val articleID: String,
    val scrollPercent: Double,
    val updatedAt: Long,
)
