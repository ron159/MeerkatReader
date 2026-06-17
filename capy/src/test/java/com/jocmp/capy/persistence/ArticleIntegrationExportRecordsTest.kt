package com.jocmp.capy.persistence

import com.jocmp.capy.ArticleIntegrationExportState
import com.jocmp.capy.InMemoryDatabaseProvider
import com.jocmp.capy.common.TimeHelpers.nowUTC
import com.jocmp.capy.db.Database
import com.jocmp.capy.fixtures.ArticleFixture
import kotlinx.coroutines.test.runTest
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArticleIntegrationExportRecordsTest {
    private lateinit var database: Database
    private lateinit var articleFixture: ArticleFixture
    private lateinit var records: ArticleIntegrationExportRecords

    @Before
    fun setup() {
        database = InMemoryDatabaseProvider()
        articleFixture = ArticleFixture(database)
        records = ArticleIntegrationExportRecords(database)
    }

    @Test
    fun upsertDeduplicatesArticleIntegrationPair() = runTest {
        val article = articleFixture.create()

        records.upsert(
            ArticleIntegrationExportInput(
                id = "first",
                articleID = article.id,
                integrationID = "wallabag",
                state = ArticleIntegrationExportState.QUEUED,
            )
        )
        records.upsert(
            ArticleIntegrationExportInput(
                id = "second",
                articleID = article.id,
                integrationID = "wallabag",
                state = ArticleIntegrationExportState.FAILED,
                errorMessage = "Unauthorized",
            )
        )

        val record = records.findByArticleAndIntegration(article.id, "wallabag")!!

        assertEquals(expected = "first", actual = record.id)
        assertEquals(expected = ArticleIntegrationExportState.FAILED, actual = record.state)
        assertEquals(expected = "Unauthorized", actual = record.errorMessage)
    }

    @Test
    fun findByStateOrdersOldestFirst() = runTest {
        val first = articleFixture.create()
        val second = articleFixture.create()

        records.upsert(
            ArticleIntegrationExportInput(
                articleID = second.id,
                integrationID = "readwise",
                state = ArticleIntegrationExportState.QUEUED,
            ),
            updatedAt = nowUTC().plusSeconds(10),
        )
        records.upsert(
            ArticleIntegrationExportInput(
                articleID = first.id,
                integrationID = "wallabag",
                state = ArticleIntegrationExportState.QUEUED,
            ),
            updatedAt = nowUTC(),
        )

        assertEquals(
            expected = listOf(first.id, second.id),
            actual = records.findByState(ArticleIntegrationExportState.QUEUED).map { it.articleID },
        )
    }

    @Test
    fun updateStateStoresRemoteID() = runTest {
        val article = articleFixture.create()
        records.upsert(
            ArticleIntegrationExportInput(
                id = "export-1",
                articleID = article.id,
                integrationID = "wallabag",
                state = ArticleIntegrationExportState.EXPORTING,
            )
        )

        records.updateState(
            id = "export-1",
            state = ArticleIntegrationExportState.EXPORTED,
            remoteID = "remote-1",
        )

        val record = records.find("export-1")!!

        assertEquals(expected = ArticleIntegrationExportState.EXPORTED, actual = record.state)
        assertEquals(expected = "remote-1", actual = record.remoteID)
    }

    @Test
    fun deleteOrphansRemovesExportsWithoutArticle() = runTest {
        val article = articleFixture.create()
        val deleted = articleFixture.create()

        records.upsert(
            ArticleIntegrationExportInput(
                articleID = article.id,
                integrationID = "wallabag",
                state = ArticleIntegrationExportState.QUEUED,
            )
        )
        records.upsert(
            ArticleIntegrationExportInput(
                articleID = deleted.id,
                integrationID = "wallabag",
                state = ArticleIntegrationExportState.QUEUED,
            )
        )

        database.articlesQueries.deleteByID(articleID = deleted.id)

        records.deleteOrphans()

        assertEquals("wallabag", records.findByArticleAndIntegration(article.id, "wallabag")?.integrationID)
        assertNull(records.findByArticleAndIntegration(deleted.id, "wallabag"))
    }
}
