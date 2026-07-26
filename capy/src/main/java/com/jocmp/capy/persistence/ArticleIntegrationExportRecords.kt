package com.jocmp.capy.persistence

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.jocmp.capy.ArticleIntegrationExportState
import com.jocmp.capy.common.TimeHelpers.nowUTC
import com.jocmp.capy.common.withIOContext
import com.jocmp.capy.db.Database
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import java.time.ZonedDateTime
import java.util.UUID

class ArticleIntegrationExportRecords(
    private val database: Database,
) {
    suspend fun find(id: String): ArticleIntegrationExportRecord? = withIOContext {
        database.articleIntegrationExportsQueries.find(
            id = id,
            mapper = ::mapper,
        ).executeAsOneOrNull()
    }

    suspend fun findByArticleAndIntegration(
        articleID: String,
        integrationID: String,
    ): ArticleIntegrationExportRecord? = withIOContext {
        database.articleIntegrationExportsQueries.findByArticleAndIntegration(
            articleID = articleID,
            integrationID = integrationID,
            mapper = ::mapper,
        ).executeAsOneOrNull()
    }

    suspend fun findByArticleIDsAndIntegration(
        articleIDs: Collection<String>,
        integrationID: String,
    ): List<ArticleIntegrationExportRecord> = withIOContext {
        articleIDs
            .distinct()
            .chunked(MAX_IDS_PER_QUERY)
            .flatMap { batch ->
                database.articleIntegrationExportsQueries.findByArticleIDsAndIntegration(
                    articleIDs = batch,
                    integrationID = integrationID,
                    mapper = ::mapper,
                ).executeAsList()
            }
    }

    fun observeByArticleIDsAndIntegration(
        articleIDs: Collection<String>,
        integrationID: String,
    ): Flow<List<ArticleIntegrationExportRecord>> {
        val queries = articleIDs
            .distinct()
            .chunked(MAX_IDS_PER_QUERY)
            .map { batch ->
                database.articleIntegrationExportsQueries.findByArticleIDsAndIntegration(
                    articleIDs = batch,
                    integrationID = integrationID,
                    mapper = ::mapper,
                ).asFlow().mapToList(Dispatchers.IO)
            }

        return queries.reduceOrNull { accumulated, next ->
            combine(accumulated, next) { first, second -> first + second }
        } ?: flowOf(emptyList())
    }

    suspend fun findByState(state: ArticleIntegrationExportState): List<ArticleIntegrationExportRecord> = withIOContext {
        database.articleIntegrationExportsQueries.findByState(
            state = state.name,
            mapper = ::mapper,
        ).executeAsList()
    }

    suspend fun upsert(
        input: ArticleIntegrationExportInput,
        updatedAt: ZonedDateTime = nowUTC(),
    ): Unit = withIOContext {
        database.articleIntegrationExportsQueries.upsert(
            id = input.id,
            articleID = input.articleID,
            integrationID = input.integrationID,
            state = input.state.name,
            remoteID = input.remoteID,
            errorMessage = input.errorMessage,
            updatedAt = updatedAt.toEpochSecond(),
        )
        Unit
    }

    suspend fun updateState(
        id: String,
        state: ArticleIntegrationExportState,
        remoteID: String? = null,
        errorMessage: String? = null,
        updatedAt: ZonedDateTime = nowUTC(),
    ): Unit = withIOContext {
        database.articleIntegrationExportsQueries.updateState(
            id = id,
            state = state.name,
            remoteID = remoteID,
            errorMessage = errorMessage,
            updatedAt = updatedAt.toEpochSecond(),
        )
        Unit
    }

    suspend fun delete(id: String): Unit = withIOContext {
        database.articleIntegrationExportsQueries.delete(id)
        Unit
    }

    suspend fun deleteOrphans(): Unit = withIOContext {
        database.articleIntegrationExportsQueries.deleteWithoutArticle()
        Unit
    }

    private fun mapper(
        id: String,
        articleID: String,
        integrationID: String,
        state: String,
        remoteID: String?,
        errorMessage: String?,
        updatedAt: Long,
    ) = ArticleIntegrationExportRecord(
        id = id,
        articleID = articleID,
        integrationID = integrationID,
        state = ArticleIntegrationExportState.from(state),
        remoteID = remoteID,
        errorMessage = errorMessage,
        updatedAt = updatedAt,
    )

    private companion object {
        const val MAX_IDS_PER_QUERY = 500
    }
}

data class ArticleIntegrationExportInput(
    val articleID: String,
    val integrationID: String,
    val state: ArticleIntegrationExportState,
    val id: String = UUID.randomUUID().toString(),
    val remoteID: String? = null,
    val errorMessage: String? = null,
)

data class ArticleIntegrationExportRecord(
    val id: String,
    val articleID: String,
    val integrationID: String,
    val state: ArticleIntegrationExportState,
    val remoteID: String?,
    val errorMessage: String?,
    val updatedAt: Long,
)
