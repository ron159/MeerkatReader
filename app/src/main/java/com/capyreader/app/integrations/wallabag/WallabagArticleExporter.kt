package com.capyreader.app.integrations.wallabag

import com.capyreader.app.preferences.AppPreferences
import com.jocmp.capy.Account
import com.jocmp.capy.ArticleIntegrationExportState
import com.jocmp.capy.common.withIOContext
import com.jocmp.capy.persistence.ArticleIntegrationExportInput
import com.jocmp.capy.persistence.ArticleIntegrationExportRecord
import com.jocmp.capy.persistence.ArticleIntegrationExportRecords
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.UUID

class WallabagArticleExporter(
    private val account: Account,
    private val records: ArticleIntegrationExportRecords,
    private val integration: WallabagIntegration,
    private val appPreferences: AppPreferences,
) {
    fun isConfigured(): Boolean = appPreferences.wallabagOptions.isConfigured()

    suspend fun queue(articleID: String): ArticleIntegrationExportRecord {
        val existing = records.findByArticleAndIntegration(articleID, integration.id)
        appPreferences.wallabagOptions.lastError.set("")
        records.upsert(
            ArticleIntegrationExportInput(
                id = existing?.id ?: UUID.randomUUID().toString(),
                articleID = articleID,
                integrationID = integration.id,
                state = ArticleIntegrationExportState.QUEUED,
            )
        )

        return requireNotNull(
            records.findByArticleAndIntegration(articleID, integration.id)
        )
    }

    suspend fun findRecords(
        articleIDs: Collection<String>,
    ): Map<String, ArticleIntegrationExportRecord> = withIOContext {
        records.findByArticleIDsAndIntegration(articleIDs, integration.id)
            .associateBy { it.articleID }
    }

    fun observeRecords(
        articleIDs: Collection<String>,
    ): Flow<Map<String, ArticleIntegrationExportRecord>> {
        return records.observeByArticleIDsAndIntegration(articleIDs, integration.id)
            .map { exportRecords -> exportRecords.associateBy { it.articleID } }
    }

    suspend fun exportQueued(limit: Int = DEFAULT_LIMIT): ExportBatchResult = withIOContext {
        val queued = records.findByState(ArticleIntegrationExportState.QUEUED)
            .filter { it.integrationID == integration.id }
            .take(limit)
        var retryableFailures = 0

        queued.forEach { record ->
            try {
                records.updateState(
                    id = record.id,
                    state = ArticleIntegrationExportState.EXPORTING,
                )
                val article = account.findArticle(record.articleID)
                    ?: throw WallabagConfigurationException("Article not found")
                integration.save(article).fold(
                    onSuccess = { result ->
                        appPreferences.wallabagOptions.lastError.set("")
                        records.updateState(
                            id = record.id,
                            state = ArticleIntegrationExportState.EXPORTED,
                            remoteID = result.remoteID,
                        )
                    },
                    onFailure = { error ->
                        val errorMessage = wallabagExportErrorMessage(error)
                        appPreferences.wallabagOptions.lastError.set(
                            errorMessage
                        )
                        if (error.isRetryable()) {
                            retryableFailures += 1
                            records.updateState(
                                id = record.id,
                                state = ArticleIntegrationExportState.QUEUED,
                                errorMessage = errorMessage,
                            )
                        } else {
                            records.updateState(
                                id = record.id,
                                state = ArticleIntegrationExportState.FAILED,
                                errorMessage = errorMessage,
                            )
                        }
                    },
                )
            } catch (e: CancellationException) {
                records.updateState(
                    id = record.id,
                    state = ArticleIntegrationExportState.QUEUED,
                )
                throw e
            } catch (e: Throwable) {
                val retryable = e.isRetryable()
                if (retryable) {
                    retryableFailures += 1
                }
                val errorMessage = wallabagExportErrorMessage(e)
                appPreferences.wallabagOptions.lastError.set(
                    errorMessage
                )
                records.updateState(
                    id = record.id,
                    state = if (retryable) {
                        ArticleIntegrationExportState.QUEUED
                    } else {
                        ArticleIntegrationExportState.FAILED
                    },
                    errorMessage = errorMessage,
                )
            }
        }

        val hasMore = queued.size == limit &&
            records.findByState(ArticleIntegrationExportState.QUEUED)
                .any { it.integrationID == integration.id }

        ExportBatchResult(
            processed = queued.size,
            retryableFailures = retryableFailures,
            hasMore = hasMore,
        )
    }

    private fun Throwable.isRetryable(): Boolean {
        return when (this) {
            is WallabagExportException -> retryable
            is IOException -> true
            else -> true
        }
    }

    companion object {
        private const val DEFAULT_LIMIT = 20
    }
}

data class ExportBatchResult(
    val processed: Int,
    val retryableFailures: Int,
    val hasMore: Boolean,
) {
    val shouldRetry: Boolean
        get() = retryableFailures > 0 || hasMore
}
