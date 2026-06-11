package com.jocmp.capy.persistence

import com.jocmp.capy.InMemoryDatabaseProvider
import com.jocmp.capy.db.Database
import com.jocmp.capy.fixtures.ArticleFixture
import kotlinx.coroutines.test.runTest
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArticleImageRecordsTest {
    private lateinit var database: Database
    private lateinit var articleFixture: ArticleFixture
    private lateinit var records: ArticleImageRecords

    @Before
    fun setup() {
        database = InMemoryDatabaseProvider()
        articleFixture = ArticleFixture(database)
        records = ArticleImageRecords(database)
    }

    @Test
    fun replaceArticleRefs_registersPendingAssets() = runTest {
        val article = articleFixture.create(url = "https://example.com/articles/post.html")

        records.replaceArticleRefs(
            articleID = article.id,
            contentHTML = """
                <img src="/images/first.jpg" alt="First">
                <img src="second.png">
            """.trimIndent(),
            articleURL = article.url.toString(),
            siteURL = null,
        )

        val refs = records.findRefs(article.id)

        assertEquals(expected = 2, actual = refs.size)
        assertEquals(
            expected = listOf(
                "https://example.com/images/first.jpg",
                "https://example.com/articles/second.png",
            ),
            actual = refs.map { it.resolvedURL },
        )
        assertTrue(refs.all { it.status == ArticleImageRecords.Status.PENDING.value })
        assertEquals(expected = "First", actual = refs.first().altText)
    }

    @Test
    fun replaceArticleRefs_replacesOldRefs() = runTest {
        val article = articleFixture.create(url = "https://example.com/articles/post.html")

        records.replaceArticleRefs(
            articleID = article.id,
            contentHTML = """<img src="/old.jpg">""",
            articleURL = article.url.toString(),
            siteURL = null,
        )
        records.replaceArticleRefs(
            articleID = article.id,
            contentHTML = """<img src="/new.jpg">""",
            articleURL = article.url.toString(),
            siteURL = null,
        )

        val refs = records.findRefs(article.id)

        assertEquals(
            expected = listOf("https://example.com/new.jpg"),
            actual = refs.map { it.resolvedURL },
        )
    }

    @Test
    fun sharedAssetsBecomeOrphanedOnlyAfterAllRefsAreRemoved() = runTest {
        val first = articleFixture.create(url = "https://example.com/articles/first.html")
        val second = articleFixture.create(url = "https://example.com/articles/second.html")
        val html = """<img src="https://cdn.example.com/shared.jpg">"""

        records.replaceArticleRefs(first.id, html, first.url.toString(), siteURL = null)
        records.replaceArticleRefs(second.id, html, second.url.toString(), siteURL = null)

        val assetID = ArticleImageRecords.assetID("https://cdn.example.com/shared.jpg")

        records.deleteRefs(listOf(first.id))

        assertEquals(expected = emptyList(), actual = records.orphanedAssetIDs())

        records.deleteRefs(listOf(second.id))

        assertEquals(expected = listOf(assetID), actual = records.orphanedAssetIDs())
    }

    @Test
    fun findCachedImagesReturnsReadyAssetsOnly() = runTest {
        val article = articleFixture.create(url = "https://example.com/articles/post.html")

        records.replaceArticleRefs(
            articleID = article.id,
            contentHTML = """
                <img src="/ready.jpg">
                <img src="/pending.jpg">
            """.trimIndent(),
            articleURL = article.url.toString(),
            siteURL = null,
        )

        val readyAssetID = ArticleImageRecords.assetID("https://example.com/ready.jpg")
        records.markReady(
            assetID = readyAssetID,
            finalURL = "https://example.com/ready.jpg",
            relativePath = "aa/$readyAssetID.jpg",
            mimeType = "image/jpeg",
            byteSize = 123,
            etag = null,
            lastModified = null,
        )

        val cachedImages = records.findCachedImages(article.id)

        assertEquals(expected = 1, actual = cachedImages.size)
        assertEquals(expected = readyAssetID, actual = cachedImages.first().assetID)
        assertEquals(expected = 0, actual = cachedImages.first().ordinal)
    }

    @Test
    fun downloadCandidatesRetriesStaleDownloads() = runTest {
        val article = articleFixture.create(url = "https://example.com/articles/post.html")

        records.replaceArticleRefs(
            articleID = article.id,
            contentHTML = """<img src="/stale.jpg">""",
            articleURL = article.url.toString(),
            siteURL = null,
        )

        val assetID = ArticleImageRecords.assetID("https://example.com/stale.jpg")

        database.articleImagesQueries.markDownloading(
            id = assetID,
            updatedAt = 0,
        )

        assertEquals(
            expected = listOf(assetID),
            actual = records.downloadCandidates().map { it.assetID },
        )
    }

    @Test
    fun downloadCandidatesStopsAfterMaxFailures() = runTest {
        val article = articleFixture.create(url = "https://example.com/articles/post.html")

        records.replaceArticleRefs(
            articleID = article.id,
            contentHTML = """<img src="/failed.jpg">""",
            articleURL = article.url.toString(),
            siteURL = null,
        )

        val assetID = ArticleImageRecords.assetID("https://example.com/failed.jpg")

        records.markFailed(assetID, "first")
        records.markFailed(assetID, "second")

        assertEquals(
            expected = listOf(assetID),
            actual = records.downloadCandidates().map { it.assetID },
        )

        records.markFailed(assetID, "third")

        assertEquals(
            expected = emptyList(),
            actual = records.downloadCandidates().map { it.assetID },
        )
    }

    @Test
    fun markPendingQueuesReadyAssetForDownloadAgain() = runTest {
        val article = articleFixture.create(url = "https://example.com/articles/post.html")

        records.replaceArticleRefs(
            articleID = article.id,
            contentHTML = """<img src="/missing.jpg">""",
            articleURL = article.url.toString(),
            siteURL = null,
        )

        val assetID = ArticleImageRecords.assetID("https://example.com/missing.jpg")
        records.markReady(
            assetID = assetID,
            finalURL = "https://example.com/missing.jpg",
            relativePath = "aa/$assetID.jpg",
            mimeType = "image/jpeg",
            byteSize = 123,
            etag = null,
            lastModified = null,
        )

        records.markPending(assetID, "Cached file missing")

        assertEquals(expected = emptyList(), actual = records.findCachedImages(article.id))
        assertEquals(
            expected = listOf(assetID),
            actual = records.downloadCandidates().map { it.assetID },
        )
    }

    @Test
    fun prunedAssetsStayOutOfDownloadCandidatesUntilRegisteredAgain() = runTest {
        val article = articleFixture.create(url = "https://example.com/articles/post.html")

        records.replaceArticleRefs(
            articleID = article.id,
            contentHTML = """<img src="/pruned.jpg">""",
            articleURL = article.url.toString(),
            siteURL = null,
        )

        val assetID = ArticleImageRecords.assetID("https://example.com/pruned.jpg")
        records.markReady(
            assetID = assetID,
            finalURL = "https://example.com/pruned.jpg",
            relativePath = "aa/$assetID.jpg",
            mimeType = "image/jpeg",
            byteSize = 123,
            etag = null,
            lastModified = null,
        )

        records.markPruned(assetID, "Cache pruned")

        assertEquals(expected = emptyList(), actual = records.downloadCandidates().map { it.assetID })

        records.replaceArticleRefs(
            articleID = article.id,
            contentHTML = """<img src="/pruned.jpg">""",
            articleURL = article.url.toString(),
            siteURL = null,
        )

        assertEquals(
            expected = listOf(assetID),
            actual = records.downloadCandidates().map { it.assetID },
        )
    }

    @Test
    fun resetDownloadingAssetsQueuesCancelledDownloads() = runTest {
        val article = articleFixture.create(url = "https://example.com/articles/post.html")

        records.replaceArticleRefs(
            articleID = article.id,
            contentHTML = """<img src="/cancelled.jpg">""",
            articleURL = article.url.toString(),
            siteURL = null,
        )

        val assetID = ArticleImageRecords.assetID("https://example.com/cancelled.jpg")

        records.markDownloading(assetID)
        records.resetDownloadingAssets("Download cancelled")

        assertEquals(
            expected = listOf(assetID),
            actual = records.downloadCandidates().map { it.assetID },
        )
    }
}
