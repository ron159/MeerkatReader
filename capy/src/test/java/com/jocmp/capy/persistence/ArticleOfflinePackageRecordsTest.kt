package com.jocmp.capy.persistence

import com.jocmp.capy.ArticleOfflinePackageState
import com.jocmp.capy.InMemoryDatabaseProvider
import com.jocmp.capy.common.TimeHelpers.nowUTC
import com.jocmp.capy.db.Database
import com.jocmp.capy.fixtures.ArticleFixture
import kotlinx.coroutines.test.runTest
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArticleOfflinePackageRecordsTest {
    private lateinit var database: Database
    private lateinit var articleFixture: ArticleFixture
    private lateinit var records: ArticleOfflinePackageRecords

    @Before
    fun setup() {
        database = InMemoryDatabaseProvider()
        articleFixture = ArticleFixture(database)
        records = ArticleOfflinePackageRecords(database)
    }

    @Test
    fun upsertStoresPackageState() = runTest {
        val article = articleFixture.create()

        records.upsert(
            ArticleOfflinePackageInput(
                articleID = article.id,
                state = ArticleOfflinePackageState.QUEUED,
                includeFullContent = true,
                includeImages = true,
                includeAudio = false,
            )
        )

        val record = records.find(article.id)!!

        assertEquals(expected = ArticleOfflinePackageState.QUEUED, actual = record.state)
        assertEquals(expected = true, actual = record.includeFullContent)
        assertEquals(expected = true, actual = record.includeImages)
        assertEquals(expected = false, actual = record.includeAudio)
        assertEquals(expected = 0, actual = record.bytes)
        assertNull(record.errorMessage)
    }

    @Test
    fun findByStateOrdersOldestFirst() = runTest {
        val first = articleFixture.create()
        val second = articleFixture.create()

        records.upsert(
            ArticleOfflinePackageInput(
                articleID = second.id,
                state = ArticleOfflinePackageState.QUEUED,
                includeFullContent = true,
                includeImages = false,
                includeAudio = false,
            ),
            updatedAt = nowUTC().plusSeconds(10),
        )
        records.upsert(
            ArticleOfflinePackageInput(
                articleID = first.id,
                state = ArticleOfflinePackageState.QUEUED,
                includeFullContent = true,
                includeImages = false,
                includeAudio = false,
            ),
            updatedAt = nowUTC(),
        )

        assertEquals(
            expected = listOf(first.id, second.id),
            actual = records.findByState(ArticleOfflinePackageState.QUEUED).map { it.articleID },
        )
    }

    @Test
    fun updateStateRecordsFailure() = runTest {
        val article = articleFixture.create()

        records.upsert(
            ArticleOfflinePackageInput(
                articleID = article.id,
                state = ArticleOfflinePackageState.DOWNLOADING,
                includeFullContent = true,
                includeImages = true,
                includeAudio = true,
            )
        )

        records.updateState(
            articleID = article.id,
            state = ArticleOfflinePackageState.FAILED,
            bytes = 123,
            errorMessage = "Network unavailable",
        )

        val record = records.find(article.id)!!

        assertEquals(expected = ArticleOfflinePackageState.FAILED, actual = record.state)
        assertEquals(expected = 123, actual = record.bytes)
        assertEquals(expected = "Network unavailable", actual = record.errorMessage)
    }

    @Test
    fun deleteOrphansRemovesPackagesWithoutArticle() = runTest {
        val article = articleFixture.create()
        val deleted = articleFixture.create()

        listOf(article, deleted).forEach {
            records.upsert(
                ArticleOfflinePackageInput(
                    articleID = it.id,
                    state = ArticleOfflinePackageState.READY,
                    includeFullContent = true,
                    includeImages = true,
                    includeAudio = false,
                )
            )
        }

        database.articlesQueries.deleteByID(articleID = deleted.id)

        records.deleteOrphans()

        assertEquals(expected = article.id, actual = records.find(article.id)?.articleID)
        assertNull(records.find(deleted.id))
    }

    @Test
    fun deleteByStateRemovesMatchingPackagesOnly() = runTest {
        val ready = articleFixture.create()
        val failed = articleFixture.create()

        records.upsert(
            ArticleOfflinePackageInput(
                articleID = ready.id,
                state = ArticleOfflinePackageState.READY,
                includeFullContent = true,
                includeImages = true,
                includeAudio = false,
            )
        )
        records.upsert(
            ArticleOfflinePackageInput(
                articleID = failed.id,
                state = ArticleOfflinePackageState.FAILED,
                includeFullContent = true,
                includeImages = true,
                includeAudio = false,
            )
        )

        assertEquals(
            expected = 1,
            actual = records.deleteByState(ArticleOfflinePackageState.FAILED),
        )
        assertEquals(expected = ready.id, actual = records.find(ready.id)?.articleID)
        assertNull(records.find(failed.id))
    }

    @Test
    fun pruneReadyPackagesKeepsNewestPackagesWithinCountLimit() = runTest {
        val first = articleFixture.create()
        val second = articleFixture.create()
        val third = articleFixture.create()

        listOf(first, second, third).forEachIndexed { index, article ->
            records.upsert(
                ArticleOfflinePackageInput(
                    articleID = article.id,
                    state = ArticleOfflinePackageState.READY,
                    includeFullContent = true,
                    includeImages = true,
                    includeAudio = false,
                    bytes = 100,
                ),
                updatedAt = nowUTC().plusSeconds(index.toLong()),
            )
        }

        assertEquals(
            expected = 1,
            actual = records.pruneReadyPackages(maxPackages = 2, maxBytes = 1_000),
        )
        assertNull(records.find(first.id))
        assertEquals(expected = second.id, actual = records.find(second.id)?.articleID)
        assertEquals(expected = third.id, actual = records.find(third.id)?.articleID)
    }

    @Test
    fun pruneReadyPackagesKeepsNewestPackagesWithinByteLimit() = runTest {
        val first = articleFixture.create()
        val second = articleFixture.create()
        val third = articleFixture.create()

        listOf(first, second, third).forEachIndexed { index, article ->
            records.upsert(
                ArticleOfflinePackageInput(
                    articleID = article.id,
                    state = ArticleOfflinePackageState.READY,
                    includeFullContent = true,
                    includeImages = true,
                    includeAudio = false,
                    bytes = 100,
                ),
                updatedAt = nowUTC().plusSeconds(index.toLong()),
            )
        }

        assertEquals(
            expected = 2,
            actual = records.pruneReadyPackages(maxPackages = 10, maxBytes = 150),
        )
        assertNull(records.find(first.id))
        assertNull(records.find(second.id))
        assertEquals(expected = third.id, actual = records.find(third.id)?.articleID)
    }

    @Test
    fun pruneReadyPackagesPreservesConfiguredArticleIDs() = runTest {
        val first = articleFixture.create()
        val second = articleFixture.create()
        val third = articleFixture.create()

        listOf(first, second, third).forEachIndexed { index, article ->
            records.upsert(
                ArticleOfflinePackageInput(
                    articleID = article.id,
                    state = ArticleOfflinePackageState.READY,
                    includeFullContent = true,
                    includeImages = true,
                    includeAudio = false,
                    bytes = 100,
                ),
                updatedAt = nowUTC().plusSeconds(index.toLong()),
            )
        }

        assertEquals(
            expected = 1,
            actual = records.pruneReadyPackages(
                maxPackages = 2,
                maxBytes = 1_000,
                preservedArticleIDs = setOf(first.id),
            ),
        )
        assertEquals(expected = first.id, actual = records.find(first.id)?.articleID)
        assertNull(records.find(second.id))
        assertEquals(expected = third.id, actual = records.find(third.id)?.articleID)
    }
}
