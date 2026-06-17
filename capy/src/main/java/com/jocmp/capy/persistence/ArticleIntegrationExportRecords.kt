package com.jocmp.capy.persistence

import com.jocmp.capy.ArticleIntegrationExportState
import com.jocmp.capy.common.TimeHelpers.nowUTC
import com.jocmp.capy.common.withIOContext
import com.jocmp.capy.db.Database
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

    suspend fun findByState(state: ArticleIntegrationExportState): List<ArticleIntegrationExportRecord> = withIOContext {
        database.articleIntegrationExportsQueries.findByState(
            state = state.name,
            mapper = ::mapper,
        ).executeAsList()
    }

    suspend fun upsert(
        input: ArticleIntegrationExportInput,
        updatedAt: ZonedDateTime = nowUTC(),
    ) = withIOContext {
        database.articleIntegrationExportsQueries.upsert(
            id = input.id,
            articleID = input.articleID,
            integrationID = input.integrationID,
            state = input.state.name,
            remoteID = input.remoteID,
            errorMessage = input.errorMessage,
            updatedAt = updatedAt.toEpochSecond(),
        )
    }

    suspend fun updateState(
        id: String,
        state: ArticleIntegrationExportState,
        remoteID: String? = null,
        errorMessage: String? = null,
        updatedAt: ZonedDateTime = nowUTC(),
    ) = withIOContext {
        database.articleIntegrationExportsQueries.updateState(
            id = id,
            state = state.name,
            remoteID = remoteID,
            errorMessage = errorMessage,
            updatedAt = updatedAt.toEpochSecond(),
        )
    }

    suspend fun delete(id: String) = withIOContext {
        database.articleIntegrationExportsQueries.delete(id)
    }

    suspend fun deleteOrphans() = withIOContext {
        database.articleIntegrationExportsQueries.deleteWithoutArticle()
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
