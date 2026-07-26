package com.jocmp.capy.persistence

import com.jocmp.capy.common.TimeHelpers.nowUTC
import com.jocmp.capy.common.withIOContext
import com.jocmp.capy.db.Database
import java.time.ZonedDateTime

class ArticleTtsProgressRecords(
    private val database: Database,
) {
    suspend fun find(
        articleID: String,
        source: ArticleTtsSource,
    ): ArticleTtsProgressRecord? = withIOContext {
        database.articleTtsProgressQueries.find(
            articleID = articleID,
            source = source.name,
            mapper = ::mapper,
        ).executeAsOneOrNull()
    }

    suspend fun upsert(
        articleID: String,
        source: ArticleTtsSource,
        contentKey: String,
        sentenceIndex: Int,
        updatedAt: ZonedDateTime = nowUTC(),
    ): Unit = withIOContext {
        database.articleTtsProgressQueries.upsert(
            articleID = articleID,
            source = source.name,
            contentKey = contentKey,
            sentenceIndex = sentenceIndex.coerceAtLeast(0).toLong(),
            updatedAt = updatedAt.toEpochSecond(),
        )
        Unit
    }

    suspend fun delete(articleID: String): Unit = withIOContext {
        database.articleTtsProgressQueries.delete(articleID)
        Unit
    }

    suspend fun deleteAll(): Unit = withIOContext {
        database.articleTtsProgressQueries.deleteAll()
        Unit
    }

    suspend fun deleteOrphans(): Unit = withIOContext {
        database.articleTtsProgressQueries.deleteWithoutArticle()
        Unit
    }

    private fun mapper(
        articleID: String,
        source: String,
        contentKey: String,
        sentenceIndex: Long,
        updatedAt: Long,
    ) = ArticleTtsProgressRecord(
        articleID = articleID,
        source = ArticleTtsSource.from(source),
        contentKey = contentKey,
        sentenceIndex = sentenceIndex.coerceAtLeast(0).toInt(),
        updatedAt = updatedAt,
    )
}

enum class ArticleTtsSource {
    ORIGINAL,
    AI_SUMMARY,
    TRANSLATION;

    companion object {
        fun from(value: String): ArticleTtsSource {
            return entries.find { it.name == value } ?: ORIGINAL
        }
    }
}

data class ArticleTtsProgressRecord(
    val articleID: String,
    val source: ArticleTtsSource,
    val contentKey: String,
    val sentenceIndex: Int,
    val updatedAt: Long,
)
