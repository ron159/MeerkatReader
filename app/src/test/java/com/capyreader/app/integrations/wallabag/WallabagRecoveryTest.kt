package com.capyreader.app.integrations.wallabag

import android.app.Application
import com.capyreader.app.common.AndroidDatabaseProvider
import com.capyreader.app.preferences.AppPreferences
import com.jocmp.capy.Account
import com.jocmp.capy.Article
import com.jocmp.capy.ArticleExportResult
import com.jocmp.capy.ArticleIntegrationExportState
import com.jocmp.capy.persistence.ArticleIntegrationExportInput
import com.jocmp.capy.persistence.ArticleIntegrationExportRecords
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WallabagRecoveryTest {
    @Test
    fun `cancelling in flight export must leave a retryable queue record`() = runBlocking {
        val records = records()
        records.upsert(record(ArticleIntegrationExportState.QUEUED))
        val integration = mockk<WallabagIntegration>()
        every { integration.id } returns WallabagIntegration.ID
        val saving = CompletableDeferred<Unit>()
        coEvery { integration.save(any()) } coAnswers {
            saving.complete(Unit)
            awaitCancellation()
        }
        val exporter = exporter(records, integration)
        val job = launch(Dispatchers.Default) { exporter.exportQueued() }
        withTimeout(5000) { saving.await() }
        job.cancelAndJoin()

        assertEquals(ArticleIntegrationExportState.QUEUED, records.find("audit-export")?.state)
    }

    @Test
    fun `worker restart must recover an exporting record left by process death`() = runBlocking {
        val records = records()
        records.upsert(record(ArticleIntegrationExportState.EXPORTING))
        val integration = mockk<WallabagIntegration>()
        every { integration.id } returns WallabagIntegration.ID
        coEvery { integration.save(any()) } returns Result.success(ArticleExportResult(remoteID = "42"))

        val result = exporter(records, integration).exportQueued()

        // This models a new worker after the prior process died while exporting.
        assertEquals(1, result.processed)
        assertEquals(ArticleIntegrationExportState.EXPORTED, records.find("audit-export")?.state)
    }

    private fun records(): ArticleIntegrationExportRecords {
        val context = RuntimeEnvironment.getApplication()
        return ArticleIntegrationExportRecords(AndroidDatabaseProvider(context).build("audit-${UUID.randomUUID()}"))
    }

    private fun record(state: ArticleIntegrationExportState) = ArticleIntegrationExportInput(
        id = "audit-export",
        articleID = "audit-article",
        integrationID = WallabagIntegration.ID,
        state = state,
    )

    private fun exporter(
        records: ArticleIntegrationExportRecords,
        integration: WallabagIntegration,
    ): WallabagArticleExporter {
        val account = mockk<Account>()
        coEvery { account.findArticle("audit-article") } returns mockk<Article>(relaxed = true)
        return WallabagArticleExporter(account, records, integration, mockk<AppPreferences>(relaxed = true))
    }
}
