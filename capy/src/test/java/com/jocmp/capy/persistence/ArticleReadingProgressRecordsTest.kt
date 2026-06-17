package com.jocmp.capy.persistence

import com.jocmp.capy.InMemoryDatabaseProvider
import com.jocmp.capy.db.Database
import com.jocmp.capy.fixtures.ArticleFixture
import kotlinx.coroutines.test.runTest
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArticleReadingProgressRecordsTest {
    private lateinit var database: Database
    private lateinit var articleFixture: ArticleFixture
    private lateinit var records: ArticleReadingProgressRecords

    @Before
    fun setup() {
        database = InMemoryDatabaseProvider()
        articleFixture = ArticleFixture(database)
        records = ArticleReadingProgressRecords(database)
    }

    @Test
    fun upsertStoresProgress() = runTest {
        val article = articleFixture.create()

        records.upsert(articleID = article.id, scrollPercent = 0.42)

        val record = records.find(article.id)!!

        assertEquals(expected = article.id, actual = record.articleID)
        assertEquals(expected = 0.42, actual = record.scrollPercent)
    }

    @Test
    fun upsertClampsProgress() = runTest {
        val article = articleFixture.create()

        records.upsert(articleID = article.id, scrollPercent = 1.5)

        assertEquals(expected = 1.0, actual = records.find(article.id)?.scrollPercent)

        records.upsert(articleID = article.id, scrollPercent = -1.0)

        assertEquals(expected = 0.0, actual = records.find(article.id)?.scrollPercent)
    }

    @Test
    fun deleteOrphansRemovesProgressWithoutArticle() = runTest {
        val article = articleFixture.create()
        val deleted = articleFixture.create()

        records.upsert(articleID = article.id, scrollPercent = 0.5)
        records.upsert(articleID = deleted.id, scrollPercent = 0.8)

        database.articlesQueries.deleteByID(articleID = deleted.id)

        records.deleteOrphans()

        assertEquals(expected = article.id, actual = records.find(article.id)?.articleID)
        assertNull(records.find(deleted.id))
    }
}
