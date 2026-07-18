package com.jocmp.capy.persistence

import com.jocmp.capy.ArticleOfflinePackageState
import com.jocmp.capy.common.TimeHelpers.nowUTC
import com.jocmp.capy.common.withIOContext
import com.jocmp.capy.db.Database
import java.time.ZonedDateTime

class ArticleOfflinePackageRecords(
    private val database: Database,
) {
    suspend fun find(articleID: String): ArticleOfflinePackageRecord? = withIOContext {
        database.articleOfflinePackagesQueries.find(
            articleID = articleID,
            mapper = ::mapper,
        ).executeAsOneOrNull()
    }

    suspend fun find(articleIDs: Collection<String>): List<ArticleOfflinePackageRecord> = withIOContext {
        articleIDs
            .distinct()
            .chunked(MAX_IDS_PER_QUERY)
            .flatMap { batch ->
                database.articleOfflinePackagesQueries.findByArticleIDs(
                    articleIDs = batch,
                    mapper = ::mapper,
                ).executeAsList()
            }
    }

    suspend fun findByState(state: ArticleOfflinePackageState): List<ArticleOfflinePackageRecord> = withIOContext {
        database.articleOfflinePackagesQueries.findByState(
            state = state.name,
            mapper = ::mapper,
        ).executeAsList()
    }

    suspend fun findAll(): List<ArticleOfflinePackageRecord> = withIOContext {
        database.articleOfflinePackagesQueries.findAll(mapper = ::mapper).executeAsList()
    }

    suspend fun upsert(
        input: ArticleOfflinePackageInput,
        updatedAt: ZonedDateTime = nowUTC(),
    ) = withIOContext {
        database.articleOfflinePackagesQueries.upsert(
            articleID = input.articleID,
            state = input.state.name,
            includeFullContent = input.includeFullContent.toSqlFlag(),
            includeImages = input.includeImages.toSqlFlag(),
            includeAudio = input.includeAudio.toSqlFlag(),
            bytes = input.bytes,
            errorMessage = input.errorMessage,
            updatedAt = updatedAt.toEpochSecond(),
        )
    }

    suspend fun updateState(
        articleID: String,
        state: ArticleOfflinePackageState,
        bytes: Long = 0,
        errorMessage: String? = null,
        updatedAt: ZonedDateTime = nowUTC(),
    ) = withIOContext {
        database.articleOfflinePackagesQueries.updateState(
            articleID = articleID,
            state = state.name,
            bytes = bytes,
            errorMessage = errorMessage,
            updatedAt = updatedAt.toEpochSecond(),
        )
    }

    suspend fun delete(articleID: String) = withIOContext {
        database.articleOfflinePackagesQueries.delete(articleID)
    }

    suspend fun deleteByState(state: ArticleOfflinePackageState): Int = withIOContext {
        database.transactionWithResult {
            val count = database.articleOfflinePackagesQueries
                .countByState(state = state.name)
                .executeAsOne()

            database.articleOfflinePackagesQueries.deleteByState(state = state.name)

            count.toInt()
        }
    }

    suspend fun pruneReadyPackages(
        maxPackages: Int,
        maxBytes: Long,
        preservedArticleIDs: Set<String> = emptySet(),
    ): Int = withIOContext {
        val readyPackages = database.articleOfflinePackagesQueries.findByState(
            state = ArticleOfflinePackageState.READY.name,
            mapper = ::mapper,
        ).executeAsList()
        val removedArticleIDs = linkedSetOf<String>()
        var currentBytes = readyPackages.sumOf { it.bytes }
        val removablePackages = readyPackages.filterNot { it.articleID in preservedArticleIDs }

        removablePackages
            .take((readyPackages.size - maxPackages).coerceAtLeast(0))
            .forEach { record ->
                removedArticleIDs += record.articleID
                currentBytes -= record.bytes
            }

        removablePackages
            .asSequence()
            .filterNot { it.articleID in removedArticleIDs }
            .forEach { record ->
                if (currentBytes <= maxBytes) {
                    return@forEach
                }

                removedArticleIDs += record.articleID
                currentBytes -= record.bytes
            }

        removedArticleIDs.chunked(MAX_IDS_PER_QUERY).forEach { batch ->
            database.articleOfflinePackagesQueries.deleteByArticleIDs(batch)
        }

        removedArticleIDs.size
    }

    suspend fun deleteAll() = withIOContext {
        database.articleOfflinePackagesQueries.deleteAll()
    }

    suspend fun deleteOrphans() = withIOContext {
        database.articleOfflinePackagesQueries.deleteWithoutArticle()
    }

    private fun mapper(
        articleID: String,
        state: String,
        includeFullContent: Long,
        includeImages: Long,
        includeAudio: Long,
        bytes: Long,
        errorMessage: String?,
        updatedAt: Long,
    ) = ArticleOfflinePackageRecord(
        articleID = articleID,
        state = ArticleOfflinePackageState.from(state),
        includeFullContent = includeFullContent.toBooleanFlag(),
        includeImages = includeImages.toBooleanFlag(),
        includeAudio = includeAudio.toBooleanFlag(),
        bytes = bytes,
        errorMessage = errorMessage,
        updatedAt = updatedAt,
    )

    private fun Boolean.toSqlFlag(): Long = if (this) 1L else 0L

    private fun Long.toBooleanFlag(): Boolean = this != 0L

    private companion object {
        const val MAX_IDS_PER_QUERY = 500
    }
}

data class ArticleOfflinePackageInput(
    val articleID: String,
    val state: ArticleOfflinePackageState,
    val includeFullContent: Boolean,
    val includeImages: Boolean,
    val includeAudio: Boolean,
    val bytes: Long = 0,
    val errorMessage: String? = null,
)

data class ArticleOfflinePackageRecord(
    val articleID: String,
    val state: ArticleOfflinePackageState,
    val includeFullContent: Boolean,
    val includeImages: Boolean,
    val includeAudio: Boolean,
    val bytes: Long,
    val errorMessage: String?,
    val updatedAt: Long,
)
