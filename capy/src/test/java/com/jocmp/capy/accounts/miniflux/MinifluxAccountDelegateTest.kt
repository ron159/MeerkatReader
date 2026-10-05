package com.jocmp.capy.accounts.miniflux

import com.jocmp.capy.AccountDelegate
import com.jocmp.capy.AccountPreferences
import com.jocmp.capy.ArticleFilter
import com.jocmp.capy.InMemoryDataStore
import com.jocmp.capy.InMemoryDatabaseProvider
import com.jocmp.capy.accounts.AddFeedResult
import com.jocmp.capy.accounts.assertAutomationApplied
import com.jocmp.capy.accounts.automationTestRule
import com.jocmp.capy.db.Database
import com.jocmp.capy.fixtures.FeedFixture
import com.jocmp.capy.persistence.ArticleRecords
import com.jocmp.capy.persistence.EnclosureRecords
import com.jocmp.capy.persistence.FeedRecords
import com.jocmp.minifluxclient.Category
import com.jocmp.minifluxclient.CreateCategoryRequest
import com.jocmp.minifluxclient.CreateFeedRequest
import com.jocmp.minifluxclient.CreateFeedResponse
import com.jocmp.minifluxclient.Enclosure
import com.jocmp.minifluxclient.Entry
import com.jocmp.minifluxclient.EntryResultSet
import com.jocmp.minifluxclient.EntryStatus
import com.jocmp.minifluxclient.Feed
import com.jocmp.minifluxclient.Icon
import com.jocmp.minifluxclient.IconData
import com.jocmp.minifluxclient.Miniflux
import com.jocmp.minifluxclient.UpdateCategoryRequest
import com.jocmp.minifluxclient.UpdateEntriesRequest
import com.jocmp.minifluxclient.UpdateFeedRequest
import com.jocmp.capy.logging.CapyLog
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.net.SocketTimeoutException
import java.time.ZonedDateTime
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MinifluxAccountDelegateTest {
    private val accountID = "777"
    private lateinit var database: Database
    private lateinit var miniflux: Miniflux
    private lateinit var feedFixture: FeedFixture
    private lateinit var delegate: AccountDelegate
    private lateinit var preferences: AccountPreferences

    private val category = Category(
        id = 1,
        title = "Tech",
        user_id = 100
    )

    private val icon = Icon(
        feed_id = 2,
        icon_id = 1
    )

    private val arsTechnicaFeed = Feed(
        id = 2,
        user_id = 100,
        title = "Ars Technica",
        site_url = "https://arstechnica.com",
        feed_url = "https://feeds.arstechnica.com/arstechnica/index",
        checked_at = "2024-02-23T17:47:45.708056Z",
        etag_header = null,
        last_modified_header = null,
        parsing_error_message = null,
        parsing_error_count = 0,
        scraper_rules = null,
        rewrite_rules = null,
        crawler = false,
        blocklist_rules = null,
        keeplist_rules = null,
        user_agent = null,
        username = null,
        password = null,
        disabled = false,
        ignore_http_cache = false,
        fetch_via_proxy = false,
        category = category,
        icon = icon
    )

    private val vergeFeed = Feed(
        id = 5,
        user_id = 100,
        title = "The Verge",
        site_url = "https://theverge.com",
        feed_url = "https://www.theverge.com/rss/index.xml",
        checked_at = "2025-02-09T14:02:28.994289Z",
        etag_header = null,
        last_modified_header = null,
        parsing_error_message = null,
        parsing_error_count = 0,
        scraper_rules = null,
        rewrite_rules = null,
        crawler = false,
        blocklist_rules = null,
        keeplist_rules = null,
        user_agent = null,
        username = null,
        password = null,
        disabled = false,
        ignore_http_cache = false,
        fetch_via_proxy = false,
        category = null,
        icon = null
    )

    private val feeds = listOf(arsTechnicaFeed, vergeFeed)
    private val categories = listOf(category)

    private val arsTechnicaArticle = Entry(
        id = 4375836222,
        user_id = 100,
        feed_id = 2,
        status = EntryStatus.UNREAD,
        hash = "abc123",
        title = "Reddit admits more moderator protests could hurt its business",
        url = "https://arstechnica.com/?p=2005526",
        comments_url = null,
        published_at = "2024-02-23T17:42:38.000000Z",
        created_at = "2024-02-23T17:47:45.708056Z",
        changed_at = "2024-02-23T17:47:45.708056Z",
        content = "<p>Reddit filed to go public on Thursday (PDF), revealing various details of the social media company's inner workings.</p>",
        author = "Scharon Harding",
        share_code = null,
        starred = false,
        reading_time = 5,
        enclosures = null,
        feed = null
    )

    private val vergeArticle = Entry(
        id = 4718104685,
        user_id = 100,
        feed_id = 5,
        status = EntryStatus.READ,
        hash = "def456",
        title = "Amazfit Active 2 review: outsized bang for your buck",
        url = "https://www.theverge.com/smartwatch-review/608342/amazfit-active-2-review",
        comments_url = null,
        published_at = "2025-02-09T14:00:00.000000Z",
        created_at = "2025-02-09T14:02:28.994289Z",
        changed_at = "2025-02-09T14:02:28.994289Z",
        content = "<p>This $130 smartwatch certainly doesn't look it.</p>",
        author = "Victoria Song",
        share_code = null,
        starred = false,
        reading_time = 8,
        enclosures = listOf(
            Enclosure(
                id = 1,
                user_id = 100,
                entry_id = 4718104685,
                url = "https://www.podtrac.com/pts/redirect.mp3/pdst.fm/e/chtbl.com/track/524GE/traffic.megaphone.fm/VMP2413819050.mp3",
                mime_type = "audio/mpeg",
                size = 45678900
            )
        ),
        feed = null
    )

    private val entries = listOf(arsTechnicaArticle, vergeArticle)

    @BeforeTest
    fun setup() {
        mockkObject(CapyLog)
        every { CapyLog.warn(any(), any()) }.returns(Unit)
        every { CapyLog.info(any(), any()) }.returns(Unit)

        database = InMemoryDatabaseProvider.build(accountID)
        feedFixture = FeedFixture(database)
        miniflux = mockk()
        preferences = AccountPreferences(InMemoryDataStore())
        delegate = MinifluxAccountDelegate(database, miniflux, preferences)
    }

    @Test
    fun refresh_updatesEntries() = runTest {
        coEvery { miniflux.feeds() }.returns(Response.success(feeds))
        coEvery { miniflux.icon(1) }.returns(
            Response.success(IconData(id = 1, data = "image/png;base64,abc", mime_type = "image/png"))
        )
        coEvery { miniflux.entries(starred = true, limit = 250, offset = 0) }.returns(
            Response.success(
                EntryResultSet(
                    total = 0,
                    entries = emptyList()
                )
            )
        )
        coEvery { miniflux.entries(status = EntryStatus.UNREAD.value, limit = 250, offset = 0) }.returns(
            Response.success(
                EntryResultSet(
                    total = 1,
                    entries = listOf(arsTechnicaArticle)
                )
            )
        )
        coEvery {
            miniflux.entries(
                limit = 250,
                offset = 0,
                order = "published_at",
                direction = "desc",
                changedAfter = null,
            )
        }.returns(
            Response.success(
                EntryResultSet(
                    total = 2,
                    entries = entries
                )
            )
        )

        delegate.refresh(ArticleFilter.default())

        val articles = database
            .articlesQueries
            .countAll(read = false, starred = false)
            .executeAsList()

        val taggedNames = database
            .feedsQueries
            .tagged()
            .executeAsList()
            .map { it.name }

        val feedsInDb = database
            .feedsQueries
            .all()
            .executeAsList()

        assertEquals(expected = 2, actual = feedsInDb.size)
        assertEquals(expected = listOf(null, "Tech"), actual = taggedNames.sortedWith(nullsFirst(naturalOrder())))
        assertEquals(expected = 1, actual = articles.size)

        val enclosures = EnclosureRecords(database).findByArticle(vergeArticle.id.toString())
        assertEquals(expected = 1, actual = enclosures.size)
    }

    @Test
    fun refresh_appliesAutomationLocallyAndSyncsRemoteStatus() = runTest {
        preferences.automationRules.set(
            listOf(automationTestRule(titleText = "Reddit admits"))
        )
        coEvery { miniflux.feeds() } returns Response.success(feeds)
        coEvery { miniflux.icon(1) } returns
            Response.success(IconData(id = 1, data = "image/png;base64,abc", mime_type = "image/png"))
        coEvery {
            miniflux.entries(starred = true, limit = 250, offset = 0)
        } returns Response.success(EntryResultSet(total = 0, entries = emptyList()))
        coEvery {
            miniflux.entries(status = EntryStatus.UNREAD.value, limit = 250, offset = 0)
        } returns Response.success(
            EntryResultSet(total = 1, entries = listOf(arsTechnicaArticle))
        )
        coEvery {
            miniflux.entries(
                limit = 250,
                offset = 0,
                order = "published_at",
                direction = "desc",
                changedAfter = null,
            )
        } returns Response.success(
            EntryResultSet(total = 1, entries = listOf(arsTechnicaArticle))
        )
        coEvery {
            miniflux.updateEntries(
                UpdateEntriesRequest(
                    entry_ids = listOf(arsTechnicaArticle.id),
                    status = EntryStatus.READ,
                )
            )
        } returns Response.success(Unit)
        coEvery { miniflux.toggleBookmark(arsTechnicaArticle.id) } returns
            Response.success(Unit)

        delegate.refresh(ArticleFilter.default()).getOrThrow()

        val articleID = arsTechnicaArticle.id.toString()
        assertAutomationApplied(database, articleID)
        coVerify {
            miniflux.updateEntries(
                UpdateEntriesRequest(
                    entry_ids = listOf(arsTechnicaArticle.id),
                    status = EntryStatus.READ,
                )
            )
        }
        coVerify { miniflux.toggleBookmark(arsTechnicaArticle.id) }
    }

    @Test
    fun refresh_skipsOnlyOldReadUnstarredArticles() = runTest {
        val cutoff = ZonedDateTime.parse("2025-03-01T00:00:00Z")
        val starredArticle = vergeArticle.copy(id = vergeArticle.id + 1, starred = true)
        val recentArticle = vergeArticle.copy(
            id = vergeArticle.id + 2,
            published_at = cutoff.plusDays(1).toString(),
        )
        val articleAtCutoff = vergeArticle.copy(
            id = vergeArticle.id + 3,
            published_at = cutoff.toString(),
        )
        stubRefresh(
            entries = listOf(
                arsTechnicaArticle,
                vergeArticle,
                starredArticle,
                recentArticle,
                articleAtCutoff,
            ),
        )

        delegate.refresh(ArticleFilter.default(), cutoffDate = cutoff).getOrThrow()

        val articles = ArticleRecords(database)
        assertNull(articles.find(vergeArticle.id.toString()))
        assertFalse(assertNotNull(articles.find(arsTechnicaArticle.id.toString())).read)
        assertTrue(assertNotNull(articles.find(starredArticle.id.toString())).starred)
        assertNotNull(articles.find(recentArticle.id.toString()))
        assertNotNull(articles.find(articleAtCutoff.id.toString()))
        assertTrue(EnclosureRecords(database).findByArticle(vergeArticle.id.toString()).isEmpty())
    }

    @Test
    fun refresh_keepsOldReadArticlesWhenAutoDeleteDisabled() = runTest {
        stubRefresh(entries = listOf(vergeArticle))

        delegate.refresh(ArticleFilter.default(), cutoffDate = null).getOrThrow()

        val article = assertNotNull(ArticleRecords(database).find(vergeArticle.id.toString()))
        assertTrue(article.read)
        assertFalse(article.starred)
    }

    @Test
    fun refresh_keepsOldReadArticlesStarredByAutomation() = runTest {
        val article = arsTechnicaArticle.copy(status = EntryStatus.READ)
        preferences.automationRules.set(
            listOf(automationTestRule(titleText = "Reddit admits"))
        )
        stubRefresh(entries = listOf(article))
        coEvery { miniflux.toggleBookmark(article.id) } returns Response.success(Unit)

        delegate.refresh(
            ArticleFilter.default(),
            cutoffDate = ZonedDateTime.parse("2025-03-01T00:00:00Z"),
        ).getOrThrow()

        assertAutomationApplied(database, article.id.toString())
    }

    @Test
    fun refresh_IOException() = runTest {
        val networkError = SocketTimeoutException("Network timeout")
        coEvery { miniflux.feeds() }.throws(networkError)

        val result = delegate.refresh(ArticleFilter.default())

        assertEquals(result, Result.failure(networkError))
    }

    @Test
    fun markRead() = runTest {
        val id = 777L

        coEvery { miniflux.updateEntries(any()) } returns Response.success(Unit)

        delegate.markRead(listOf(id.toString()))

        coVerify {
            miniflux.updateEntries(
                UpdateEntriesRequest(
                    entry_ids = listOf(id),
                    status = EntryStatus.READ
                )
            )
        }
    }

    @Test
    fun markUnread() = runTest {
        val id = 777L

        coEvery { miniflux.updateEntries(any()) } returns Response.success(Unit)

        delegate.markUnread(listOf(id.toString()))

        coVerify {
            miniflux.updateEntries(
                UpdateEntriesRequest(
                    entry_ids = listOf(id),
                    status = EntryStatus.UNREAD
                )
            )
        }
    }

    @Test
    fun addStar() = runTest {
        val id = 777L

        coEvery { miniflux.toggleBookmark(any()) } returns Response.success(Unit)

        delegate.addStar(listOf(id.toString()))

        coVerify { miniflux.toggleBookmark(id) }
    }

    @Test
    fun removeStar() = runTest {
        val id = 777L

        coEvery { miniflux.toggleBookmark(any()) } returns Response.success(Unit)

        delegate.removeStar(listOf(id.toString()))

        coVerify { miniflux.toggleBookmark(id) }
    }

    @Test
    fun markRead_httpError() = runTest {
        coEvery {
            miniflux.updateEntries(
                UpdateEntriesRequest(entry_ids = listOf(777L), status = EntryStatus.READ)
            )
        } returns Response.error(503, "Unavailable".toResponseBody())

        val result = delegate.markRead(listOf("777"))

        assertEquals(503, assertIs<HttpException>(result.exceptionOrNull()).code())
    }

    @Test
    fun markUnread_httpError() = runTest {
        coEvery {
            miniflux.updateEntries(
                UpdateEntriesRequest(entry_ids = listOf(777L), status = EntryStatus.UNREAD)
            )
        } returns Response.error(503, "Unavailable".toResponseBody())

        val result = delegate.markUnread(listOf("777"))

        assertEquals(503, assertIs<HttpException>(result.exceptionOrNull()).code())
    }

    @Test
    fun addStar_httpError() = runTest {
        coEvery { miniflux.toggleBookmark(777L) } returns
            Response.error(503, "Unavailable".toResponseBody())

        val result = delegate.addStar(listOf("777"))

        assertEquals(503, assertIs<HttpException>(result.exceptionOrNull()).code())
    }

    @Test
    fun removeStar_httpError() = runTest {
        coEvery { miniflux.toggleBookmark(777L) } returns
            Response.error(503, "Unavailable".toResponseBody())

        val result = delegate.removeStar(listOf("777"))

        assertEquals(503, assertIs<HttpException>(result.exceptionOrNull()).code())
    }

    @Test
    fun removeFeed_httpError() = runTest {
        val feed = feedFixture.create()
        coEvery { miniflux.deleteFeed(feedID = feed.id.toLong()) } returns
            Response.error(503, "Unavailable".toResponseBody())

        val result = delegate.removeFeed(feed)

        assertEquals(503, assertIs<HttpException>(result.exceptionOrNull()).code())
        assertEquals(feed, FeedRecords(database).find(feed.id))
    }

    @Test
    fun updateFolder_httpErrorPreservesLocalFolder() = runTest {
        feedFixture.create(folderNames = listOf(category.title))
        coEvery { miniflux.categories() } returns Response.success(categories)
        coEvery {
            miniflux.updateCategory(category.id, UpdateCategoryRequest(title = "News"))
        } returns Response.error(503, "Unavailable".toResponseBody())

        val result = delegate.updateFolder(oldTitle = category.title, newTitle = "News")

        assertEquals(503, assertIs<HttpException>(result.exceptionOrNull()).code())
        assertEquals(
            listOf(category.title),
            database.feedsQueries.tagged().executeAsList().map { it.name },
        )
    }

    @Test
    fun removeFolder_httpErrorPreservesLocalFolder() = runTest {
        feedFixture.create(folderNames = listOf(category.title))
        coEvery { miniflux.categories() } returns Response.success(categories)
        coEvery { miniflux.deleteCategory(category.id) } returns
            Response.error(503, "Unavailable".toResponseBody())

        val result = delegate.removeFolder(folderTitle = category.title)

        assertEquals(503, assertIs<HttpException>(result.exceptionOrNull()).code())
        assertEquals(
            listOf(category.title),
            database.feedsQueries.tagged().executeAsList().map { it.name },
        )
    }

    @Test
    fun updateFolder_categoriesHttpError() = runTest {
        coEvery { miniflux.categories() } returns
            Response.error(503, "Unavailable".toResponseBody())

        val result = delegate.updateFolder(oldTitle = category.title, newTitle = "News")

        assertEquals(503, assertIs<HttpException>(result.exceptionOrNull()).code())
    }

    @Test
    fun removeFolder_categoriesHttpError() = runTest {
        coEvery { miniflux.categories() } returns
            Response.error(503, "Unavailable".toResponseBody())

        val result = delegate.removeFolder(folderTitle = category.title)

        assertEquals(503, assertIs<HttpException>(result.exceptionOrNull()).code())
    }

    @Test
    fun addFeed() = runTest {
        val url = "https://wheresyoured.at/feed"
        val feedID = 2819820L

        coEvery {
            miniflux.createFeed(CreateFeedRequest(feed_url = url, category_id = null))
        } returns Response.success(CreateFeedResponse(feed_id = feedID))

        coEvery { miniflux.feed(feedID) }.returns(Response.success(arsTechnicaFeed))
        coEvery { miniflux.icon(1) }.returns(
            Response.success(IconData(id = 1, data = "image/png;base64,abc", mime_type = "image/png"))
        )

        coEvery { miniflux.entries(starred = true, limit = any(), offset = any()) }.returns(
            Response.success(EntryResultSet(total = 0, entries = emptyList()))
        )
        coEvery { miniflux.entries(status = EntryStatus.UNREAD.value, limit = any(), offset = any()) }.returns(
            Response.success(EntryResultSet(total = 0, entries = emptyList()))
        )
        coEvery {
            miniflux.entries(
                limit = any(),
                offset = any(),
                order = any(),
                direction = any()
            )
        }.returns(Response.success(EntryResultSet(total = 0, entries = emptyList())))

        val result = delegate.addFeed(
            url = url,
            folderTitles = emptyList(),
            title = ""
        ) as AddFeedResult.Success
        val feed = result.feed

        assertEquals(
            expected = "Ars Technica",
            actual = feed.title
        )
    }

    @Test
    fun addFeed_categoriesHttpError() = runTest {
        coEvery { miniflux.categories() } returns
            Response.error(503, "Unavailable".toResponseBody())

        val result = delegate.addFeed(
            url = "https://example.com/feed",
            title = "News",
            folderTitles = listOf("Tech"),
        )

        assertIs<AddFeedResult.Error.NetworkError>(assertIs<AddFeedResult.Failure>(result).error)
        coVerify(exactly = 0) {
            miniflux.createCategory(any())
            miniflux.createFeed(any())
        }
    }

    @Test
    fun addFeed_Failure() = runTest {
        val url = "https://example.com/invalid"

        coEvery {
            miniflux.createFeed(CreateFeedRequest(feed_url = url, category_id = null))
        } returns Response.error(404, mockk(relaxed = true))

        val result = delegate.addFeed(url = url, folderTitles = emptyList(), title = "")

        assertTrue(result is AddFeedResult.Failure)
    }

    @Test
    fun updateFeed_modifyTitle() = runTest {
        val feed = feedFixture.create()
        val feedTitle = "The Verge Mobile Podcast"

        coEvery {
            miniflux.updateFeed(
                feedID = feed.id.toLong(),
                request = UpdateFeedRequest(title = feedTitle, category_id = null)
            )
        }.returns(Response.success(vergeFeed))

        val updated = delegate.updateFeed(
            feed = feed,
            title = feedTitle,
            folderTitles = emptyList(),
        ).getOrThrow()

        assertEquals(expected = feedTitle, actual = updated.title)
    }

    @Test
    fun updateFeed_httpErrorPreservesLocalTitleAndFolder() = runTest {
        val feed = feedFixture.create(folderNames = listOf(category.title))
        val newCategory = Category(id = 2, title = "News", user_id = 100)
        coEvery { miniflux.categories() } returns
            Response.success(listOf(category, newCategory))
        coEvery {
            miniflux.updateFeed(
                feedID = feed.id.toLong(),
                request = UpdateFeedRequest(title = "New title", category_id = newCategory.id),
            )
        } returns Response.error(503, "Unavailable".toResponseBody())

        val result = delegate.updateFeed(
            feed = feed,
            title = "New title",
            folderTitles = listOf(newCategory.title),
        )

        assertEquals(503, assertIs<HttpException>(result.exceptionOrNull()).code())
        assertEquals(feed, FeedRecords(database).find(feed.id))
        assertEquals(
            listOf(category.title),
            database.feedsQueries.tagged().executeAsList().map { it.name },
        )
    }

    @Test
    fun updateFeed_modifyCategoryToExisting() = runTest {
        val feed = feedFixture.create(folderNames = listOf("Tech"))
        val newCategory = Category(id = 2, title = "News", user_id = 100)

        coEvery {
            miniflux.categories()
        }.returns(Response.success(listOf(category, newCategory)))

        coEvery {
            miniflux.updateFeed(
                feedID = feed.id.toLong(),
                request = UpdateFeedRequest(title = feed.title, category_id = newCategory.id)
            )
        }.returns(Response.success(vergeFeed))

        delegate.updateFeed(
            feed = feed,
            title = feed.title,
            folderTitles = listOf("News"),
        ).getOrThrow()

        val allTaggings = database.taggingsQueries
            .findFeedTaggingsToDelete(feedID = feed.id, excludedNames = emptyList())
            .executeAsList()

        val nonNewsTaggings = database.taggingsQueries
            .findFeedTaggingsToDelete(feedID = feed.id, excludedNames = listOf("News"))
            .executeAsList()

        assertEquals(expected = 1, actual = allTaggings.size)
        assertEquals(expected = 0, actual = nonNewsTaggings.size)
    }

    @Test
    fun updateFeed_newCategory() = runTest {
        val feed = feedFixture.create(folderNames = listOf("Tech"))
        val createdCategory = Category(id = 3, title = "Science", user_id = 100)

        coEvery {
            miniflux.categories()
        }.returns(Response.success(listOf(category)))

        coEvery {
            miniflux.createCategory(CreateCategoryRequest(title = "Science"))
        }.returns(Response.success(createdCategory))

        coEvery {
            miniflux.updateFeed(
                feedID = feed.id.toLong(),
                request = UpdateFeedRequest(title = feed.title, category_id = createdCategory.id)
            )
        }.returns(Response.success(vergeFeed))

        delegate.updateFeed(
            feed = feed,
            title = feed.title,
            folderTitles = listOf("Science"),
        ).getOrThrow()

        val allTaggings = database.taggingsQueries
            .findFeedTaggingsToDelete(feedID = feed.id, excludedNames = emptyList())
            .executeAsList()

        val nonScienceTaggings = database.taggingsQueries
            .findFeedTaggingsToDelete(feedID = feed.id, excludedNames = listOf("Science"))
            .executeAsList()

        assertEquals(expected = 1, actual = allTaggings.size)
        assertEquals(expected = 0, actual = nonScienceTaggings.size)
    }

    private fun stubRefresh(entries: List<Entry>) {
        coEvery { miniflux.feeds() } returns Response.success(feeds)
        coEvery { miniflux.icon(1) } returns
            Response.success(IconData(id = 1, data = "image/png;base64,abc", mime_type = "image/png"))
        val starred = entries.filter { it.starred }
        coEvery { miniflux.entries(starred = true, limit = 250, offset = 0) } returns
            Response.success(EntryResultSet(total = starred.size, entries = starred))
        val unread = entries.filter { it.status == EntryStatus.UNREAD }
        coEvery {
            miniflux.entries(status = EntryStatus.UNREAD.value, limit = 250, offset = 0)
        } returns Response.success(EntryResultSet(total = unread.size, entries = unread))
        coEvery {
            miniflux.entries(
                limit = 250,
                offset = 0,
                order = "published_at",
                direction = "desc",
                changedAfter = null,
            )
        } returns Response.success(EntryResultSet(total = entries.size, entries = entries))
    }
}
