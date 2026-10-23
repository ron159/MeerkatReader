package com.capyreader.app.offline

import android.app.Application
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import com.capyreader.app.articleimages.ArticleImageDownloader
import com.capyreader.app.common.AndroidDatabaseProvider
import com.capyreader.app.preferences.AppPreferences
import com.jocmp.capy.Account
import com.jocmp.capy.Article
import com.jocmp.capy.ArticleOfflinePackageState
import com.jocmp.capy.Feed
import com.jocmp.capy.Enclosure
import com.jocmp.capy.db.Database
import com.jocmp.capy.persistence.ArticleFullContentRecords
import com.jocmp.capy.persistence.ArticleImageRecords
import com.jocmp.capy.persistence.ArticleOfflinePackageRecords
import com.jocmp.capy.persistence.ArticleReadingProgressRecords
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.URL
import java.time.ZonedDateTime
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class OfflinePackageRecoveryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var database: Database
    private lateinit var records: ArticleOfflinePackageRecords
    private lateinit var downloader: ArticleOfflinePackageDownloader
    private lateinit var account: Account
    private lateinit var audioStore: ArticleOfflineAudioStore
    private var failSecondAudio = true
    private val articles = mutableMapOf<String, Article>()

    @Before
    fun setUp() {
        database = AndroidDatabaseProvider(context).build("offline-audit-${UUID.randomUUID()}")
        records = ArticleOfflinePackageRecords(database)
        account = mockk<Account>()
        val appPreferences = mockk<AppPreferences>(relaxed = true)
        every { appPreferences.offlineOptions.enabled.get() } returns false
        every { appPreferences.offlineOptions.storageLimitMegabytes.get() } returns 1024
        every { appPreferences.offlineOptions.preserveStarred.get() } returns false
        every { appPreferences.offlineOptions.preserveSavedForLater.get() } returns false
        every { appPreferences.offlineOptions.preserveRecentlyOpened.get() } returns false
        every { appPreferences.offlineOptions.preserveFeedOffline.get() } returns false
        coEvery { account.findArticle(any()) } coAnswers { articles[firstArg<String>()] }
        coEvery { account.findFeed("audit-feed") } returns Feed(
            id = "audit-feed",
            subscriptionID = "audit-feed",
            title = "Manual offline feed",
            feedURL = "https://example.com/feed",
            offlinePolicy = null,
        )
        audioStore = ArticleOfflineAudioStore(temporaryFolder.root)
        val audioHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
            val code = if (failSecondAudio && chain.request().url.encodedPath == "/second.mp3") 503 else 200
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code == 200) "OK" else "Unavailable")
                .body("audio bytes".toResponseBody("audio/mpeg".toMediaType()))
                .build()
        }.build()
        downloader = ArticleOfflinePackageDownloader(
            account = account,
            packageRecords = records,
            fullContentRecords = ArticleFullContentRecords(database),
            imageRecords = mockk<ArticleImageRecords>(relaxed = true),
            readingProgressRecords = mockk<ArticleReadingProgressRecords>(relaxed = true),
            imageDownloader = mockk<ArticleImageDownloader>(relaxed = true),
            audioDownloader = ArticleOfflineAudioDownloader(audioHttpClient, audioStore),
            audioStore = audioStore,
            appPreferences = appPreferences,
        )
        startKoin { modules(module { single { downloader } }) }
        database.feedsQueries.upsert(
            id = "audit-feed",
            subscription_id = "audit-feed",
            title = "Manual offline feed",
            feed_url = "https://example.com/feed",
            site_url = "https://example.com",
            favicon_url = null,
            priority = null,
            itunes_image_url = null,
            read_later = false,
        )
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `manual offline package must survive cleanup and retry after a temporary error`() = runBlocking {
        seedArticle("retry-article")
        downloader.queue("retry-article", includeFullContent = true, includeImages = false)
        var fetchAttempts = 0
        coEvery { account.fetchFullContent(any()) } coAnswers {
            fetchAttempts += 1
            if (fetchAttempts == 1) {
                kotlin.Result.failure(IOException("temporary network error"))
            } else {
                kotlin.Result.success("<p>Recovered content</p>")
            }
        }

        val first = worker().doWork()
        assertEquals(Result.retry(), first)
        val second = worker(runAttemptCount = 1).doWork()

        assertEquals("WorkManager retry must attempt the retained package again", 2, fetchAttempts)
        assertEquals(records.find("retry-article").toString(), Result.success(), second)
        assertEquals(ArticleOfflinePackageState.READY, records.find("retry-article")?.state)
    }

    @Test
    fun `a successful full batch must request continuation while a queued item remains`() = runBlocking {
        repeat(51) { index ->
            val articleID = "batch-$index"
            seedArticle(articleID)
            downloader.queue(articleID, includeFullContent = false, includeImages = false)
        }

        val result = downloader.downloadQueued()
        assertEquals(50, result.processed)
        assertEquals(0, result.failed)
        assertEquals(1, records.findByState(ArticleOfflinePackageState.QUEUED).size)

        assertTrue("one package is still queued and needs a subsequent worker run", result.shouldRetry)
    }

    @Test
    fun `permanent download failures stop retrying and remain available for manual retry`() = runBlocking {
        seedArticle("permanent-error")
        downloader.queue("permanent-error", includeFullContent = true, includeImages = false)
        coEvery { account.fetchFullContent(any()) } returns kotlin.Result.failure(IOException("unavailable"))

        assertEquals(Result.retry(), worker(runAttemptCount = 0).doWork())
        assertEquals(Result.retry(), worker(runAttemptCount = 1).doWork())
        assertEquals(Result.failure(), worker(runAttemptCount = 2).doWork())
        assertEquals(ArticleOfflinePackageState.FAILED, records.find("permanent-error")?.state)
        assertEquals("unavailable", records.find("permanent-error")?.errorMessage)

        coEvery { account.fetchFullContent(any()) } returns kotlin.Result.success("<p>Available now</p>")
        downloader.queue("permanent-error", includeFullContent = true, includeImages = false)
        assertEquals(Result.success(), worker().doWork())
        assertEquals(ArticleOfflinePackageState.READY, records.find("permanent-error")?.state)
    }

    @Test
    fun `a batch after the automatic retry budget still drains new queued packages`() = runBlocking {
        seedArticle("failed-article")
        downloader.queue("failed-article", includeFullContent = true, includeImages = false)
        coEvery { account.fetchFullContent(any()) } returns kotlin.Result.failure(IOException("unavailable"))
        assertTrue(downloader.downloadQueued().shouldRetry)
        repeat(51) { index ->
            val id = "remaining-$index"
            seedArticle(id)
            downloader.queue(id, includeFullContent = false, includeImages = false)
        }

        assertEquals(Result.success(), worker(runAttemptCount = 3).doWork())
        assertEquals(51, records.findByState(ArticleOfflinePackageState.READY).size)
        assertEquals(ArticleOfflinePackageState.FAILED, records.find("failed-article")?.state)
        assertFalse(records.findByState(ArticleOfflinePackageState.QUEUED).isNotEmpty())
    }

    @Test
    fun `a temporary failure after the first 150 packages still receives an automatic retry`() = runBlocking {
        val attempts = mutableMapOf<String, Int>()
        repeat(200) { index ->
            val id = "large-$index"
            seedArticle(id)
            downloader.queue(id, includeFullContent = true, includeImages = false)
            records.updateState(
                articleID = id,
                state = ArticleOfflinePackageState.QUEUED,
                updatedAt = ZonedDateTime.parse("2026-10-23T00:00:00Z").plusSeconds(index.toLong()),
            )
        }
        coEvery { account.fetchFullContent(any()) } coAnswers {
            val article = firstArg<Article>()
            val attempt = attempts.getOrDefault(article.id, 0) + 1
            attempts[article.id] = attempt
            if (article.id == "large-175" && attempt == 1) {
                kotlin.Result.failure(IOException("temporary late failure"))
            } else {
                kotlin.Result.success("<p>Full content</p>")
            }
        }

        assertEquals(Result.retry(), worker(runAttemptCount = 0).doWork())
        assertEquals(200, attempts.size)
        assertEquals(199, records.findByState(ArticleOfflinePackageState.READY).size)
        assertEquals(Result.success(), worker(runAttemptCount = 1).doWork())
        assertEquals(2, attempts["large-175"])
        assertEquals(201, attempts.values.sum())
        assertEquals(200, records.findByState(ArticleOfflinePackageState.READY).size)
    }

    @Test
    fun `cleanup removes partial failed audio but retains retry metadata and ready audio`() = runBlocking {
        val failedID = "partial-audio"
        val readyID = "ready-audio"
        seedArticle(failedID)
        seedArticle(readyID)
        val firstURL = "https://example.com/first.mp3"
        val secondURL = "https://example.com/second.mp3"
        val readyURL = "https://example.com/ready.mp3"
        fun enclosure(url: String) = Enclosure(URL(url), "audio/mpeg", null, null)
        articles[failedID] = articles.getValue(failedID).copy(
            enclosures = listOf(enclosure(firstURL), enclosure(secondURL)),
        )
        articles[readyID] = articles.getValue(readyID).copy(enclosures = listOf(enclosure(readyURL)))
        downloader.queue(failedID, includeFullContent = false, includeImages = false, includeAudio = true)
        downloader.queue(readyID, includeFullContent = false, includeImages = false, includeAudio = true)

        val first = downloader.downloadQueued()
        assertEquals(1, first.failed)
        assertNotNull(audioStore.localURL(failedID, firstURL, "audio/mpeg"))
        downloader.cleanup()

        assertEquals(ArticleOfflinePackageState.FAILED, records.find(failedID)?.state)
        assertEquals("Audio download failed: HTTP 503", records.find(failedID)?.errorMessage)
        assertNull(audioStore.localURL(failedID, firstURL, "audio/mpeg"))
        assertNotNull(audioStore.localURL(readyID, readyURL, "audio/mpeg"))

        failSecondAudio = false
        assertEquals(Result.success(), worker(runAttemptCount = 1).doWork())
        assertEquals(ArticleOfflinePackageState.READY, records.find(failedID)?.state)
        assertNotNull(audioStore.localURL(failedID, firstURL, "audio/mpeg"))
        assertNotNull(audioStore.localURL(failedID, secondURL, "audio/mpeg"))
    }

    private fun worker(runAttemptCount: Int = 0) =
        TestListenableWorkerBuilder<ArticleOfflinePackageWorker>(context)
            .setRunAttemptCount(runAttemptCount)
            .build()

    private fun seedArticle(id: String) {
        val date = ZonedDateTime.parse("2026-10-23T00:00:00Z")
        articles[id] = Article(
            id = id,
            feedID = "audit-feed",
            title = "Article $id",
            author = null,
            contentHTML = "<p>Feed content</p>",
            url = URL("https://example.com/$id"),
            summary = "Feed summary",
            imageURL = null,
            updatedAt = date,
            publishedAt = date,
            read = false,
            starred = false,
        )
        database.articlesQueries.create(
            id = id,
            feed_id = "audit-feed",
            title = "Article $id",
            author = null,
            content_html = "<p>Feed content</p>",
            extracted_content_url = null,
            url = "https://example.com/$id",
            summary = "Feed summary",
            image_url = null,
            published_at = date.toEpochSecond(),
            enclosure_type = null,
        )
        database.articlesQueries.createStatus(
            article_id = id,
            updated_at = date.toEpochSecond(),
            read = false,
        )
    }
}
