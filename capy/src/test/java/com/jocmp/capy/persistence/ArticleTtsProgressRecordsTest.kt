package com.jocmp.capy.persistence

import com.jocmp.capy.InMemoryDatabaseProvider
import com.jocmp.capy.db.Database
import com.jocmp.capy.fixtures.ArticleFixture
import kotlinx.coroutines.test.runTest
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArticleTtsProgressRecordsTest {
    private lateinit var database: Database
    private lateinit var articleFixture: ArticleFixture
    private lateinit var records: ArticleTtsProgressRecords

    @Before
    fun setup() {
        database = InMemoryDatabaseProvider()
        articleFixture = ArticleFixture(database)
        records = ArticleTtsProgressRecords(database)
    }

    @Test
    fun upsertKeepsIndependentSourceProgress() = runTest {
        val article = articleFixture.create()

        records.upsert(
            articleID = article.id,
            source = ArticleTtsSource.ORIGINAL,
            contentKey = "original-v1",
            sentenceIndex = 2,
        )
        records.upsert(
            articleID = article.id,
            source = ArticleTtsSource.TRANSLATION,
            contentKey = "translation-v1",
            sentenceIndex = 5,
        )

        val original = records.find(article.id, ArticleTtsSource.ORIGINAL)!!
        val translation = records.find(article.id, ArticleTtsSource.TRANSLATION)!!
        assertEquals(2, original.sentenceIndex)
        assertEquals(5, translation.sentenceIndex)
        assertEquals("original-v1", original.contentKey)
        assertEquals("translation-v1", translation.contentKey)
    }

    @Test
    fun deleteOrphansRemovesDeletedArticles() = runTest {
        val article = articleFixture.create()
        records.upsert(
            articleID = article.id,
            source = ArticleTtsSource.ORIGINAL,
            contentKey = "original-v1",
            sentenceIndex = 1,
        )

        database.articlesQueries.deleteByID(article.id)
        records.deleteOrphans()

        assertNull(records.find(article.id, ArticleTtsSource.ORIGINAL))
    }
}
