package com.capyreader.app.ui.articles

import android.app.Application
import app.cash.turbine.test
import com.capyreader.app.articleimages.ArticleImageCacheCleaner
import com.capyreader.app.articleimages.ArticleImageDownloader
import com.capyreader.app.articleimages.ArticleImagePreloader
import com.capyreader.app.articleimages.ArticleImageStore
import com.capyreader.app.ai.ArticleAiAction
import com.capyreader.app.ai.ArticleAiRepository
import com.capyreader.app.integrations.wallabag.WallabagArticleExporter
import com.capyreader.app.integrations.wallabag.WallabagIntegration
import com.capyreader.app.notifications.NotificationHelper
import com.capyreader.app.offline.ArticleOfflinePackageDownloader
import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.preferences.ArticleListVerticalSwipe
import com.capyreader.app.refresher.RefreshInterval
import com.capyreader.app.ui.articles.feeds.AngleRefreshState
import com.jocmp.capy.Account
import com.jocmp.capy.Article
import com.jocmp.capy.ArticleFilter
import com.jocmp.capy.ArticleIntegrationExportState
import com.jocmp.capy.ArticleStatus
import com.jocmp.capy.Feed
import com.jocmp.capy.Folder
import com.jocmp.capy.accounts.Source
import com.jocmp.capy.persistence.ArticleFullContentRecords
import com.jocmp.capy.persistence.ArticleImageRecords
import com.jocmp.capy.persistence.ArticleIntegrationExportRecord
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.net.URL
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ArticleScreenViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var account: Account
    private lateinit var appPreferences: AppPreferences
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var articleImageRecords: ArticleImageRecords
    private lateinit var articleImagePreloader: ArticleImagePreloader
    private lateinit var articleImageDownloader: ArticleImageDownloader
    private lateinit var articleImageStore: ArticleImageStore
    private lateinit var articleImageCacheCleaner: ArticleImageCacheCleaner
    private lateinit var articleFullContentRecords: ArticleFullContentRecords
    private lateinit var articleAiRepository: ArticleAiRepository
    private lateinit var articleOfflinePackageDownloader: ArticleOfflinePackageDownloader
    private lateinit var wallabagArticleExporter: WallabagArticleExporter

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        account = mockk(relaxed = true) {
            every { folders } returns flowOf(emptyList())
            every { feeds } returns flowOf(emptyList())
            every { taggedFeeds } returns flowOf(emptyList())
            every { savedSearches } returns flowOf(emptyList())
            every { canSaveArticleExternally } returns mockk(relaxed = true) {
                every { get() } returns false
                every { stateIn(any()) } returns MutableStateFlow(false)
                every { changes() } returns flowOf(false)
            }
            every { countAll(any()) } returns flowOf(emptyMap())
            every { countAllBySavedSearch(any()) } returns flowOf(emptyMap())
            every { source } returns Source.LOCAL
            coEvery { refresh(any()) } returns Result.success(Unit)
            every { preferences } returns mockk(relaxed = true) {
                every { lastRefreshedAt } returns mockk(relaxed = true) {
                    every { get() } returns 0L
                }
            }
        }

        appPreferences = AppPreferences(RuntimeEnvironment.getApplication()).also {
            it.clearAll()
            it.refreshInterval.set(RefreshInterval.EVERY_TWO_HOURS)
        }

        notificationHelper = mockk(relaxed = true)
        articleImageRecords = mockk(relaxed = true)
        coEvery { articleImageRecords.findCachedImages(any()) } returns emptyList()
        articleImagePreloader = mockk(relaxed = true)
        articleImageDownloader = mockk(relaxed = true)
        articleImageStore = mockk(relaxed = true)
        articleImageCacheCleaner = mockk(relaxed = true)
        articleFullContentRecords = mockk(relaxed = true)
        articleAiRepository = mockk(relaxed = true)
        articleOfflinePackageDownloader = mockk(relaxed = true)
        wallabagArticleExporter = mockk(relaxed = true)
        coEvery { articleFullContentRecords.find(any()) } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        stopKoin()
    }

    @Test
    fun `refreshAll triggers initial refresh`() = runTest {
        val viewModel = buildViewModel()

        assertFalse(viewModel.refreshInitialized)

        advanceUntilIdle()

        assertTrue(viewModel.refreshInitialized)
    }

    @Test
    fun `skips initial refresh when account has already synced before`() = runTest {
        every { account.preferences.lastRefreshedAt.get() } returns
            ZonedDateTime.parse("2023-11-14T22:13:20Z").toEpochSecond()

        val viewModel = buildViewModel()

        assertTrue(viewModel.refreshInitialized)
        assertFalse(viewModel.refreshingAll)
    }

    @Test
    fun `refreshAll transitions from stopped, running, to settling`() = runTest {
        every { account.preferences.lastRefreshedAt.get() } returns
            ZonedDateTime.parse("2023-11-14T22:13:20Z").toEpochSecond()

        val viewModel = buildViewModel()

        viewModel.refreshAllState.test {
            assertEquals(AngleRefreshState.STOPPED, awaitItem())

            viewModel.refreshAll()
            assertEquals(AngleRefreshState.RUNNING, awaitItem())

            advanceUntilIdle()

            assertEquals(AngleRefreshState.SETTLING, awaitItem())
        }
    }

    @Test
    fun `refreshAll guards against double calls while running`() = runTest {
        every { account.preferences.lastRefreshedAt.get() } returns
            ZonedDateTime.parse("2023-11-14T22:13:20Z").toEpochSecond()

        val viewModel = buildViewModel()

        viewModel.refreshAllState.test {
            assertEquals(AngleRefreshState.STOPPED, awaitItem())

            viewModel.refreshAll()
            assertEquals(AngleRefreshState.RUNNING, awaitItem())

            viewModel.refreshAll()

            advanceUntilIdle()

            assertEquals(AngleRefreshState.SETTLING, awaitItem())
        }
    }

    @Test
    fun `selecting a visible article does not wait for initial refresh`() = runTest {
        val refreshGate = CompletableDeferred<Unit>()
        coEvery { account.refresh(any()) } coAnswers {
            refreshGate.await()
            Result.success(Unit)
        }
        val viewModel = buildViewModel()
        runCurrent()
        assertTrue(viewModel.refreshingAll)

        val visibleArticle = Article(
            id = "visible-article",
            feedID = "feed",
            title = "Visible article",
            author = null,
            contentHTML = "<p>Cached content</p>",
            url = URL("https://example.com/article"),
            summary = "Cached summary",
            imageURL = null,
            updatedAt = ZonedDateTime.now(),
            publishedAt = ZonedDateTime.now(),
            read = false,
            starred = false,
        )
        var selectedArticle: Article? = null

        viewModel.selectArticle(visibleArticle) {
            selectedArticle = it
        }

        assertEquals(visibleArticle.id, viewModel.article?.id)
        assertEquals(visibleArticle.id, selectedArticle?.id)

        refreshGate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `requestNextFeed opens next feed after current feed's unread count drops to zero`() = runTest {
        val initialFilter = ArticleFilter.Feeds(
            feedID = "1",
            folderTitle = null,
            feedStatus = ArticleStatus.UNREAD
        )
        appPreferences.filter.set(initialFilter)
        appPreferences.articleListOptions.swipeBottom.set(ArticleListVerticalSwipe.NEXT_FEED)

        val feedA = Feed(id = "1", subscriptionID = "1", title = "A", feedURL = "a", count = 3)
        val feedB = Feed(id = "2", subscriptionID = "2", title = "B", feedURL = "b", count = 3)

        val feeds = MutableStateFlow(listOf(feedA, feedB))
        val counts = MutableStateFlow<Map<String, Long>>(mapOf("1" to 3, "2" to 3))

        every { account.feeds } returns feeds
        every { account.countAll(any()) } returns counts

        val viewModel = buildViewModel()
        advanceUntilIdle()

        counts.value = mapOf("1" to 0, "2" to 3)
        advanceUntilIdle()

        viewModel.requestNextFeed()
        advanceUntilIdle()

        val expectedNext = ArticleFilter.Feeds(
            feedID = "2",
            folderTitle = null,
            feedStatus = ArticleStatus.UNREAD
        )
        assertEquals(expectedNext, appPreferences.filter.get())
    }

    @Test
    fun `requestNextFeed skips empty children when current folder is marked read`() = runTest {
        val initialFilter = ArticleFilter.Folders(
            folderTitle = "X",
            folderStatus = ArticleStatus.UNREAD,
        )
        appPreferences.filter.set(initialFilter)
        appPreferences.articleListOptions.swipeBottom.set(ArticleListVerticalSwipe.NEXT_FEED)

        val x1 = Feed(id = "x1", subscriptionID = "x1", title = "X1", feedURL = "x1", folderName = "X")
        val x2 = Feed(id = "x2", subscriptionID = "x2", title = "X2", feedURL = "x2", folderName = "X")
        val y1 = Feed(id = "y1", subscriptionID = "y1", title = "Y1", feedURL = "y1", folderName = "Y")

        val folderX = Folder(title = "X", feeds = listOf(x1, x2), expanded = true)
        val folderY = Folder(title = "Y", feeds = listOf(y1), expanded = true)

        val folders = MutableStateFlow(listOf(folderX, folderY))
        val counts = MutableStateFlow<Map<String, Long>>(
            mapOf("x1" to 2L, "x2" to 2L, "y1" to 3L)
        )

        every { account.folders } returns folders
        every { account.feeds } returns flowOf(emptyList())
        every { account.countAll(any()) } returns counts

        val viewModel = buildViewModel()
        advanceUntilIdle()

        counts.value = mapOf("x1" to 0L, "x2" to 0L, "y1" to 3L)
        advanceUntilIdle()

        viewModel.requestNextFeed()
        advanceUntilIdle()

        assertEquals(
            ArticleFilter.Folders(folderTitle = "Y", folderStatus = ArticleStatus.UNREAD),
            appPreferences.filter.get()
        )
    }

    @Test
    fun `cached article preview is identified without another provider request`() = runTest {
        val article = previewArticle("cached")
        coEvery {
            articleAiRepository.cachedResult(ArticleAiAction.PREVIEW_SUMMARY, article)
        } returns "Cached summary"
        val viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.summarizeArticlePreviews(listOf(article))
        advanceUntilIdle()

        assertEquals(
            ArticleAiPreviewState(
                result = "Cached summary",
                isCached = true,
            ),
            viewModel.aiSummaryPreviews[article.id],
        )
        coVerify(exactly = 0) {
            articleAiRepository.run(
                action = ArticleAiAction.PREVIEW_SUMMARY,
                article = article,
                forceRefresh = true,
            )
        }
    }

    @Test
    fun `uncached article preview performs one fresh request`() = runTest {
        val article = previewArticle("fresh")
        coEvery {
            articleAiRepository.cachedResult(ArticleAiAction.PREVIEW_SUMMARY, article)
        } returns null
        coEvery {
            articleAiRepository.run(
                action = ArticleAiAction.PREVIEW_SUMMARY,
                article = article,
                forceRefresh = true,
            )
        } returns Result.success("Fresh summary")
        val viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.summarizeArticlePreviews(listOf(article))
        advanceUntilIdle()

        assertEquals(
            ArticleAiPreviewState(result = "Fresh summary"),
            viewModel.aiSummaryPreviews[article.id],
        )
        coVerify(exactly = 1) {
            articleAiRepository.run(
                action = ArticleAiAction.PREVIEW_SUMMARY,
                article = article,
                forceRefresh = true,
            )
        }
    }

    @Test
    fun `Wallabag export ignores unconfigured integration and missing URL`() = runTest {
        var enqueueCount = 0
        val viewModel = buildViewModel(
            enqueueWallabagExport = { enqueueCount += 1 },
        )
        val article = previewArticle("wallabag-no-op")

        every { wallabagArticleExporter.isConfigured() } returns false
        viewModel.exportToWallabagAsync(article)
        every { wallabagArticleExporter.isConfigured() } returns true
        viewModel.exportToWallabagAsync(article.copy(url = null))
        advanceUntilIdle()

        coVerify(exactly = 0) { wallabagArticleExporter.queue(any()) }
        assertEquals(0, enqueueCount)
        assertTrue(viewModel.wallabagExportRecords.isEmpty())
    }

    @Test
    fun `Wallabag export queues and schedules exactly once`() = runTest {
        var enqueueCount = 0
        val article = previewArticle("wallabag-valid")
        val queuedRecord = wallabagRecord(
            articleID = article.id,
            state = ArticleIntegrationExportState.QUEUED,
        )
        every { wallabagArticleExporter.isConfigured() } returns true
        coEvery {
            wallabagArticleExporter.queue(article.id)
        } returns queuedRecord
        val viewModel = buildViewModel(
            enqueueWallabagExport = { enqueueCount += 1 },
        )

        viewModel.exportToWallabagAsync(article)
        advanceUntilIdle()

        assertEquals(queuedRecord, viewModel.wallabagExportRecords[article.id])
        coVerify(exactly = 1) { wallabagArticleExporter.queue(article.id) }
        assertEquals(1, enqueueCount)
    }

    @Test
    fun `Wallabag observation replaces removes and prunes visible records`() = runTest {
        val firstArticle = previewArticle("wallabag-first")
        val secondArticle = previewArticle("wallabag-second")
        val firstQueued = wallabagRecord(
            articleID = firstArticle.id,
            state = ArticleIntegrationExportState.QUEUED,
        )
        val secondQueued = wallabagRecord(
            articleID = secondArticle.id,
            state = ArticleIntegrationExportState.QUEUED,
        )
        val firstExported = wallabagRecord(
            articleID = firstArticle.id,
            state = ArticleIntegrationExportState.EXPORTED,
        )
        val firstVisibleRecords = MutableStateFlow(
            mapOf(
                firstArticle.id to firstQueued,
                secondArticle.id to secondQueued,
            )
        )
        val narrowedRecords = MutableStateFlow(
            mapOf(firstArticle.id to firstExported)
        )
        every { wallabagArticleExporter.isConfigured() } returns true
        every {
            wallabagArticleExporter.observeRecords(
                listOf(firstArticle.id, secondArticle.id)
            )
        } returns firstVisibleRecords
        every {
            wallabagArticleExporter.observeRecords(listOf(firstArticle.id))
        } returns narrowedRecords
        val viewModel = buildViewModel()

        viewModel.observeWallabagExportStates(
            listOf(firstArticle, secondArticle)
        )
        runCurrent()
        assertEquals(firstQueued, viewModel.wallabagExportRecords[firstArticle.id])
        assertEquals(secondQueued, viewModel.wallabagExportRecords[secondArticle.id])

        firstVisibleRecords.value = mapOf(
            firstArticle.id to firstExported,
            secondArticle.id to secondQueued,
        )
        runCurrent()
        assertEquals(firstExported, viewModel.wallabagExportRecords[firstArticle.id])

        viewModel.observeWallabagExportStates(listOf(firstArticle))
        runCurrent()
        assertEquals(firstExported, viewModel.wallabagExportRecords[firstArticle.id])
        assertFalse(viewModel.wallabagExportRecords.containsKey(secondArticle.id))

        narrowedRecords.value = emptyMap()
        runCurrent()
        assertTrue(viewModel.wallabagExportRecords.isEmpty())

        viewModel.observeWallabagExportStates(emptyList())
    }

    private fun previewArticle(id: String): Article {
        val now = ZonedDateTime.parse("2026-07-25T00:00:00Z")
        return Article(
            id = id,
            feedID = "feed",
            title = "Article $id",
            author = null,
            contentHTML = "<p>Content</p>",
            url = URL("https://example.com/$id"),
            summary = "",
            imageURL = null,
            updatedAt = now,
            publishedAt = now,
            read = false,
            starred = false,
        )
    }

    private fun buildViewModel(
        syncFlushInterval: kotlin.time.Duration? = null,
        enqueueWallabagExport: () -> Unit = {},
    ): ArticleScreenViewModel {
        val application = RuntimeEnvironment.getApplication() as Application

        return ArticleScreenViewModel(
            account = account,
            appPreferences = appPreferences,
            application = application,
            notificationHelper = notificationHelper,
            articleImageRecords = articleImageRecords,
            articleImagePreloader = articleImagePreloader,
            articleImageDownloader = articleImageDownloader,
            articleImageStore = articleImageStore,
            articleImageCacheCleaner = articleImageCacheCleaner,
            articleFullContentRecords = articleFullContentRecords,
            articleAiRepository = articleAiRepository,
            articleOfflinePackageDownloader = articleOfflinePackageDownloader,
            wallabagArticleExporter = wallabagArticleExporter,
            enqueueWallabagExport = enqueueWallabagExport,
            ioDispatcher = testDispatcher,
            syncFlushInterval = syncFlushInterval,
        )
    }

    private fun wallabagRecord(
        articleID: String,
        state: ArticleIntegrationExportState,
    ) = ArticleIntegrationExportRecord(
        id = "$articleID-$state",
        articleID = articleID,
        integrationID = WallabagIntegration.ID,
        state = state,
        remoteID = null,
        errorMessage = null,
        updatedAt = 0L,
    )
}
