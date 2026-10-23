package com.jocmp.capy.persistence

import com.jocmp.capy.Article
import com.jocmp.capy.ArticleFilter
import com.jocmp.capy.ArticleSearchQuery
import com.jocmp.capy.ArticleStatus
import com.jocmp.capy.Feed
import com.jocmp.capy.FeedPriority
import com.jocmp.capy.InMemoryDatabaseProvider
import com.jocmp.capy.MarkRead
import com.jocmp.capy.articles.SortOrder
import com.jocmp.capy.db.Database
import com.jocmp.capy.fixtures.AccountFixture
import com.jocmp.capy.fixtures.ArticleFixture
import com.jocmp.capy.fixtures.FeedFixture
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ArticleSearchScopeTest {
    @JvmField
    @Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var database: Database
    private lateinit var records: ArticleRecords
    private lateinit var techFeed: Feed
    private lateinit var sportsFeed: Feed
    private val now = ZonedDateTime.now(ZoneOffset.UTC)
    private val savedSearchID = "search-scope"
    private val feedIDs get() = listOf(techFeed.id, sportsFeed.id)

    @Before
    fun setup() {
        database = InMemoryDatabaseProvider.build("search-scope")
        records = ArticleRecords(database)
        val feeds = FeedFixture(database)
        techFeed = feeds.create(title = "Tech Feed", folderNames = listOf("Tech", "Technology"))
        sportsFeed = feeds.create(title = "Sports Feed", folderNames = listOf("Sports"))
        SavedSearchRecords(database).upsert(id = savedSearchID, name = "All test articles")
    }

    @Test
    fun folderQualifierFiltersListsCountsAndBulkReadAcrossScopes() {
        val tech = createArticle("tech", techFeed)
        createArticle("sports", sportsFeed)
        val since = now.toOffsetDateTime()
        val query = ArticleSearchQuery.parse("folder:tech")

        SortOrder.entries.forEach { sort ->
            lists(query, since, sort).forEach { (scope, articles) ->
                assertEquals(listOf(tech.id), articles.map { it.id }, "$scope $sort list")
            }
            unreadIDs(query, sort).forEach { (scope, ids) ->
                assertEquals(listOf(tech.id), ids, "$scope $sort bulk read")
            }
        }
        counts(query, since).forEach { (scope, count) ->
            assertEquals(1L, count, "$scope count must not duplicate multiple matching folders")
        }
        assertTrue(
            records.byFeed.all(
                feedIDs = listOf(sportsFeed.id), status = ArticleStatus.ALL,
                searchQuery = query, since = since, limit = 100, offset = 0,
                sortOrder = SortOrder.NEWEST_FIRST, priority = FeedPriority.FEED,
            ).executeAsList().isEmpty(),
            "A folder qualifier must intersect the selected feed",
        )
    }

    @Test
    fun folderSearchMarkAllReadLeavesOtherFoldersUnread() = runTest {
        val tech = createArticle("tech", techFeed)
        val sports = createArticle("sports", sportsFeed)
        val account = AccountFixture.create(parentFolder = temporaryFolder, database = database)
        val ids = account.unreadArticleIDs(
            filter = ArticleFilter.Articles(ArticleStatus.ALL), range = MarkRead.All,
            sortOrder = SortOrder.NEWEST_FIRST, query = "folder:Tech",
        )

        account.markAllRead(ids).getOrThrow()

        assertTrue(assertNotNull(records.find(tech.id)).read)
        assertFalse(assertNotNull(records.find(sports.id)).read)
    }

    @Test
    fun readQualifierIncludesOlderManualReadsAcrossScopes() {
        val manualRead = createArticle("manual-read", techFeed)
        val remoteRead = createArticle("remote-read", techFeed, read = true)
        createArticle("unread", sportsFeed)
        records.markAllRead(listOf(manualRead.id), lastReadAt = now.minusDays(1))
        val query = ArticleSearchQuery.parse("is:read")
        val since = now.toOffsetDateTime()
        val expected = setOf(manualRead.id, remoteRead.id)

        SortOrder.entries.forEach { sort ->
            lists(query, since, sort).forEach { (scope, articles) ->
                assertEquals(expected, articles.map { it.id }.toSet(), "$scope $sort list")
            }
            unreadIDs(query, sort).forEach { (scope, ids) ->
                assertTrue(ids.isEmpty(), "$scope is:read must not mark unrelated unread articles")
            }
        }
        listOf(since, null).forEach { countSince ->
            counts(query, countSince).forEach { (scope, count) ->
                assertEquals(2L, count, "$scope read count")
            }
        }
    }

    @Test
    fun unreadListsStillKeepArticlesReadSinceOpeningTheScreen() {
        val oldRead = createArticle("old-read", techFeed)
        val recentRead = createArticle("recent-read", techFeed)
        val unread = createArticle("unread", sportsFeed)
        val since = now.toOffsetDateTime().minusMinutes(5)
        records.markAllRead(listOf(oldRead.id), lastReadAt = now.minusDays(1))
        records.markAllRead(listOf(recentRead.id), lastReadAt = now)
        val query = ArticleSearchQuery.parse("is:unread")

        lists(query, since).forEach { (scope, articles) ->
            assertEquals(setOf(recentRead.id, unread.id), articles.map { it.id }.toSet(), scope)
        }
        counts(query, since).forEach { (scope, count) -> assertEquals(2L, count, scope) }
        counts(query, null).forEach { (scope, count) -> assertEquals(1L, count, scope) }
        unreadIDs(query).forEach { (scope, ids) -> assertEquals(listOf(unread.id), ids, scope) }
    }

    private fun createArticle(id: String, feed: Feed, read: Boolean = false): Article {
        val article = ArticleFixture(database).create(
            id = id, feed = feed, read = read, publishedAt = now.toEpochSecond(),
        )
        SavedSearchRecords(database).upsertArticle(articleID = id, savedSearchID = savedSearchID)
        return article
    }

    private fun lists(
        query: ArticleSearchQuery,
        since: OffsetDateTime,
        sort: SortOrder = SortOrder.NEWEST_FIRST,
    ): Map<String, List<Article>> = mapOf(
        "all" to records.byStatus.all(
            status = ArticleStatus.ALL, searchQuery = query, since = since,
            limit = 100, offset = 0, sortOrder = sort,
        ).executeAsList(),
        "feed" to records.byFeed.all(
            feedIDs = feedIDs, status = ArticleStatus.ALL, searchQuery = query, since = since,
            limit = 100, offset = 0, sortOrder = sort, priority = FeedPriority.FEED,
        ).executeAsList(),
        "saved search" to records.bySavedSearch.all(
            savedSearchID = savedSearchID, status = ArticleStatus.ALL, searchQuery = query,
            since = since, limit = 100, offset = 0, sortOrder = sort,
        ).executeAsList(),
        "today" to records.byToday.all(
            status = ArticleStatus.ALL, searchQuery = query, since = since,
            limit = 100, offset = 0, sortOrder = sort,
        ).executeAsList(),
    )

    private fun counts(query: ArticleSearchQuery, since: OffsetDateTime?): Map<String, Long> = mapOf(
        "all" to records.byStatus.count(ArticleStatus.ALL, query, since).executeAsOne(),
        "feed" to records.byFeed.count(
            feedIDs = feedIDs, status = ArticleStatus.ALL, searchQuery = query, since = since,
            priority = FeedPriority.FEED,
        ).executeAsOne(),
        "saved search" to records.bySavedSearch.count(
            savedSearchID = savedSearchID, status = ArticleStatus.ALL,
            searchQuery = query, since = since,
        ).executeAsOne(),
        "today" to records.byToday.count(ArticleStatus.ALL, query, since).executeAsOne(),
    )

    private fun unreadIDs(
        query: ArticleSearchQuery,
        sort: SortOrder = SortOrder.NEWEST_FIRST,
    ): Map<String, List<String>> = mapOf(
        "all" to records.byStatus.unreadArticleIDs(
            ArticleStatus.ALL, range = MarkRead.All, sortOrder = sort, searchQuery = query,
        ).executeAsList(),
        "feed" to records.byFeed.unreadArticleIDs(
            ArticleStatus.ALL, feedIDs = feedIDs, range = MarkRead.All, sortOrder = sort,
            priority = FeedPriority.FEED, searchQuery = query,
        ).executeAsList(),
        "saved search" to records.bySavedSearch.unreadArticleIDs(
            ArticleStatus.ALL, savedSearchID = savedSearchID, range = MarkRead.All,
            sortOrder = sort, searchQuery = query,
        ).executeAsList(),
        "today" to records.byToday.unreadArticleIDs(
            ArticleStatus.ALL, range = MarkRead.All, sortOrder = sort, searchQuery = query,
        ).executeAsList(),
    )
}
