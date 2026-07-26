package com.capyreader.app.integrations.wallabag

import com.capyreader.app.preferences.AppPreferences
import com.jocmp.capy.Account
import com.jocmp.capy.Article
import com.jocmp.capy.ArticleExportResult
import com.jocmp.capy.ArticleIntegrationExportState
import com.jocmp.capy.persistence.ArticleIntegrationExportRecord
import com.jocmp.capy.persistence.ArticleIntegrationExportRecords
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.URL
import java.time.ZonedDateTime

class WallabagArticleExporterTest {
    private val account = mockk<Account>()
    private val records = mockk<ArticleIntegrationExportRecords>(relaxed = true)
    private val integration = mockk<WallabagIntegration>()
    private val appPreferences = mockk<AppPreferences>(relaxed = true)
    private val exporter = WallabagArticleExporter(
        account = account,
        records = records,
        integration = integration,
        appPreferences = appPreferences,
    )

    init {
        every { integration.id } returns WallabagIntegration.ID
    }

    @Test
    fun `successful export stores remote id`() = runTest {
        val record = queuedRecord()
        val article = article()
        coEvery {
            records.findByState(ArticleIntegrationExportState.QUEUED)
        } returns listOf(record)
        coEvery { account.findArticle(article.id) } returns article
        coEvery {
            integration.save(article)
        } returns Result.success(ArticleExportResult(remoteID = "42"))

        val result = exporter.exportQueued()

        assertFalse(result.shouldRetry)
        coVerifyOrder {
            records.updateState(
                id = record.id,
                state = ArticleIntegrationExportState.EXPORTING,
                updatedAt = any(),
            )
            records.updateState(
                id = record.id,
                state = ArticleIntegrationExportState.EXPORTED,
                remoteID = "42",
                updatedAt = any(),
            )
        }
    }

    @Test
    fun `temporary server failure stays queued for worker retry`() = runTest {
        val record = queuedRecord()
        val article = article()
        coEvery {
            records.findByState(ArticleIntegrationExportState.QUEUED)
        } returns listOf(record)
        coEvery { account.findArticle(article.id) } returns article
        coEvery {
            integration.save(article)
        } returns Result.failure(WallabagHttpException(503))

        val result = exporter.exportQueued()

        assertTrue(result.shouldRetry)
        coVerify {
            records.updateState(
                id = record.id,
                state = ArticleIntegrationExportState.QUEUED,
                errorMessage = "Wallabag request failed (HTTP 503)",
                updatedAt = any(),
            )
        }
    }

    @Test
    fun `authentication failure is visible and does not retry`() = runTest {
        val record = queuedRecord()
        val article = article()
        coEvery {
            records.findByState(ArticleIntegrationExportState.QUEUED)
        } returns listOf(record)
        coEvery { account.findArticle(article.id) } returns article
        coEvery {
            integration.save(article)
        } returns Result.failure(WallabagAuthenticationException())

        val result = exporter.exportQueued()

        assertFalse(result.shouldRetry)
        coVerify {
            records.updateState(
                id = record.id,
                state = ArticleIntegrationExportState.FAILED,
                errorMessage = "Wallabag authentication failed",
                updatedAt = any(),
            )
        }
    }

    @Test
    fun `unexpected network failure stores a stable redacted message`() = runTest {
        val record = queuedRecord()
        val article = article()
        coEvery {
            records.findByState(ArticleIntegrationExportState.QUEUED)
        } returns listOf(record)
        coEvery { account.findArticle(article.id) } returns article
        coEvery {
            integration.save(article)
        } returns Result.failure(
            IOException("Bearer server-secret raw-response-payload")
        )

        val result = exporter.exportQueued()

        assertTrue(result.shouldRetry)
        coVerify {
            records.updateState(
                id = record.id,
                state = ArticleIntegrationExportState.QUEUED,
                errorMessage = "Wallabag connection failed",
                updatedAt = any(),
            )
        }
    }

    private fun queuedRecord() = ArticleIntegrationExportRecord(
        id = "export-1",
        articleID = "article-1",
        integrationID = WallabagIntegration.ID,
        state = ArticleIntegrationExportState.QUEUED,
        remoteID = null,
        errorMessage = null,
        updatedAt = 0,
    )

    private fun article() = Article(
        id = "article-1",
        feedID = "feed-1",
        title = "Example article",
        author = null,
        contentHTML = "",
        url = URL("https://example.com/article"),
        summary = "",
        imageURL = null,
        updatedAt = ZonedDateTime.now(),
        publishedAt = ZonedDateTime.now(),
        read = false,
        starred = false,
    )
}
