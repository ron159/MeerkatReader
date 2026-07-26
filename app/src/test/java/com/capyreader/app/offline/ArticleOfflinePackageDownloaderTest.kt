package com.capyreader.app.offline

import com.capyreader.app.articleimages.ArticleImageDownloader
import com.capyreader.app.preferences.AppPreferences
import com.jocmp.capy.Account
import com.jocmp.capy.Article
import com.jocmp.capy.ArticleOfflinePackageState
import com.jocmp.capy.Feed
import com.jocmp.capy.persistence.ArticleFullContentRecords
import com.jocmp.capy.persistence.ArticleImageRecords
import com.jocmp.capy.persistence.ArticleOfflinePackageInput
import com.jocmp.capy.persistence.ArticleOfflinePackageRecord
import com.jocmp.capy.persistence.ArticleOfflinePackageRecords
import com.jocmp.capy.persistence.ArticleReadingProgressRecords
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.URL
import java.time.ZonedDateTime

class ArticleOfflinePackageDownloaderTest {
    private val account = mockk<Account>(relaxed = true)
    private val packageRecords = mockk<ArticleOfflinePackageRecords>(relaxed = true)
    private val fullContentRecords = mockk<ArticleFullContentRecords>(relaxed = true)
    private val imageRecords = mockk<ArticleImageRecords>(relaxed = true)
    private val readingProgressRecords = mockk<ArticleReadingProgressRecords>(relaxed = true)
    private val imageDownloader = mockk<ArticleImageDownloader>(relaxed = true)
    private val audioDownloader = mockk<ArticleOfflineAudioDownloader>(relaxed = true)
    private val audioStore = mockk<ArticleOfflineAudioStore>(relaxed = true)
    private val appPreferences = mockk<AppPreferences>(relaxed = true)

    private val downloader = ArticleOfflinePackageDownloader(
        account = account,
        packageRecords = packageRecords,
        fullContentRecords = fullContentRecords,
        imageRecords = imageRecords,
        readingProgressRecords = readingProgressRecords,
        imageDownloader = imageDownloader,
        audioDownloader = audioDownloader,
        audioStore = audioStore,
        appPreferences = appPreferences,
    )

    @Test
    fun `download lifecycle stores full content images audio and ready bytes`() = runTest {
        val article = article()
        val offlinePackage = packageRecord(
            includeFullContent = true,
            includeImages = true,
            includeAudio = true,
        )
        val fullContent = "<article><p>Downloaded full content</p></article>"

        coEvery {
            packageRecords.findByState(ArticleOfflinePackageState.QUEUED)
        } returns listOf(offlinePackage)
        coEvery {
            packageRecords.findByState(ArticleOfflinePackageState.STALE)
        } returns emptyList()
        coEvery { account.findArticle(article.id) } returns article
        coEvery { account.findFeed(article.feedID) } returns feed()
        coEvery { fullContentRecords.find(article.id) } returns null
        coEvery { account.fetchFullContent(article) } returns Result.success(fullContent)
        coEvery { imageRecords.readyBytesForArticle(article.id) } returns 30L
        coEvery { audioDownloader.download(article) } returns 40L

        val result = downloader.downloadQueued()

        assertEquals(1, result.processed)
        assertEquals(0, result.failed)
        assertFalse(result.shouldRetry)
        coVerify { fullContentRecords.upsert(article.id, fullContent) }
        verify {
            imageRecords.replaceArticleRefs(
                articleID = article.id,
                contentHTML = fullContent,
                articleURL = article.url.toString(),
                siteURL = article.siteURL,
            )
        }
        coVerify { imageDownloader.downloadPendingForArticle(article.id) }
        coVerify { audioDownloader.download(article) }
        coVerify {
            packageRecords.updateState(
                articleID = article.id,
                state = ArticleOfflinePackageState.READY,
                bytes = fullContent.toByteArray().size + 70L,
                updatedAt = any(),
            )
        }
    }

    @Test
    fun `failed audio download remains retryable and succeeds after requeue`() = runTest {
        val article = article()
        val offlinePackage = packageRecord(
            includeFullContent = false,
            includeImages = false,
            includeAudio = true,
        )
        var attempt = 0

        coEvery {
            packageRecords.findByState(ArticleOfflinePackageState.QUEUED)
        } returns listOf(offlinePackage)
        coEvery {
            packageRecords.findByState(ArticleOfflinePackageState.STALE)
        } returns emptyList()
        coEvery { account.findArticle(article.id) } returns article
        coEvery { account.findFeed(article.feedID) } returns feed()
        coEvery { audioDownloader.download(article) } coAnswers {
            attempt += 1
            if (attempt == 1) {
                throw IOException("Audio unavailable")
            }
            40L
        }

        val failed = downloader.downloadQueued()
        downloader.queue(
            articleID = article.id,
            includeFullContent = false,
            includeImages = false,
            includeAudio = true,
        )
        val retried = downloader.downloadQueued()

        assertTrue(failed.shouldRetry)
        assertFalse(retried.shouldRetry)
        coVerify {
            packageRecords.updateState(
                articleID = article.id,
                state = ArticleOfflinePackageState.FAILED,
                errorMessage = "Audio unavailable",
                updatedAt = any(),
            )
        }
        coVerify {
            packageRecords.upsert(
                ArticleOfflinePackageInput(
                    articleID = article.id,
                    state = ArticleOfflinePackageState.QUEUED,
                    includeFullContent = false,
                    includeImages = false,
                    includeAudio = true,
                ),
                updatedAt = any(),
            )
        }
        coVerify {
            packageRecords.updateState(
                articleID = article.id,
                state = ArticleOfflinePackageState.READY,
                bytes = article.defaultContent.toByteArray().size + 40L,
                updatedAt = any(),
            )
        }
    }

    @Test
    fun `clear all removes owned audio before package metadata`() = runTest {
        downloader.clearAll()

        coVerifyOrder {
            audioStore.deleteAll()
            packageRecords.deleteAll()
        }
    }

    private fun packageRecord(
        includeFullContent: Boolean,
        includeImages: Boolean,
        includeAudio: Boolean,
    ) = ArticleOfflinePackageRecord(
        articleID = ARTICLE_ID,
        state = ArticleOfflinePackageState.QUEUED,
        includeFullContent = includeFullContent,
        includeImages = includeImages,
        includeAudio = includeAudio,
        bytes = 0,
        errorMessage = null,
        updatedAt = 0,
    )

    private fun article() = Article(
        id = ARTICLE_ID,
        feedID = FEED_ID,
        title = "Offline article",
        author = "Author",
        contentHTML = "<p>Feed content</p>",
        url = URL("https://example.com/article"),
        summary = "Summary",
        imageURL = null,
        updatedAt = ZonedDateTime.parse("2026-07-25T00:00:00Z"),
        publishedAt = ZonedDateTime.parse("2026-07-25T00:00:00Z"),
        read = false,
        starred = false,
        siteURL = "https://example.com",
    )

    private fun feed() = Feed(
        id = FEED_ID,
        subscriptionID = FEED_ID,
        title = "Feed",
        feedURL = "https://example.com/feed",
    )

    private companion object {
        const val ARTICLE_ID = "article-1"
        const val FEED_ID = "feed-1"
    }
}
