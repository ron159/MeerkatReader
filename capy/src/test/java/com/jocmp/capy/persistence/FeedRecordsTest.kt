package com.jocmp.capy.persistence

import com.jocmp.capy.InMemoryDatabaseProvider
import com.jocmp.capy.FeedOfflinePolicy
import com.jocmp.capy.awaitRepeated
import com.jocmp.capy.db.Database
import com.jocmp.capy.fixtures.ArticleFixture
import com.jocmp.capy.fixtures.FeedFixture
import kotlinx.coroutines.test.runTest
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeedRecordsTest {
    private lateinit var database: Database
    private lateinit var articleFixture: ArticleFixture

    @Before
    fun setup() {
        database = InMemoryDatabaseProvider.build("777")
        articleFixture = ArticleFixture(database)
    }

    @Test
    fun removeFeed_cleansUpRecords() {
        val feedRecords = FeedRecords(database)
        val article = articleFixture.create()

        feedRecords.removeFeed(feedID = article.feedID)

        val result = database
            .articlesQueries
            .findBy(articleID = article.id)
            .executeAsOneOrNull()

        assertNull(result)
    }

    @Test
    fun updateStickyFullContent() = runTest {
        val records = FeedRecords(database)
        var feed = FeedFixture(database, records = records).create()

        assertFalse(feed.enableStickyFullContent)

        records.updateStickyFullContent(enabled = true, feedID = feed.id)

        feed = records.find(feed.id)!!

        assertTrue(feed.enableStickyFullContent)
    }

    @Test
    fun upsert_preservesStickyFullContent() = runTest {
        val records = FeedRecords(database)
        val feed = FeedFixture(database, records = records).create()

        records.updateStickyFullContent(enabled = true, feedID = feed.id)

        records.upsert(
            feedID = feed.id,
            subscriptionID = feed.subscriptionID,
            title = "Updated feed",
            feedURL = feed.feedURL,
            siteURL = feed.siteURL,
            faviconURL = feed.faviconURL,
        )

        assertTrue(records.find(feed.id)?.enableStickyFullContent ?: false)
    }

    @Test
    fun clearStickyFullContent() = runTest {
        val records = FeedRecords(database)
        val feeds = 3.awaitRepeated {
            val feed = FeedFixture(database, records = records).create()
            records.updateStickyFullContent(enabled = true, feedID = feed.id)

            records.find(feed.id)!!
        }

        assertEquals(
            expected = setOf(true),
            actual = feeds.map { it.enableStickyFullContent }.toSet()
        )

        records.clearStickyFullContent()

        val updated = feeds.map { records.find(it.id)!! }
        assertEquals(
            expected = setOf(false),
            actual = updated.map { it.enableStickyFullContent }.toSet()
        )
    }

    @Test
    fun updateOfflinePolicy() = runTest {
        val records = FeedRecords(database)
        val feed = FeedFixture(database, records = records).create()

        assertNull(feed.offlinePolicy)

        records.updateOfflinePolicy(feedID = feed.id, policy = FeedOfflinePolicy.ALWAYS)

        assertEquals(
            expected = FeedOfflinePolicy.ALWAYS,
            actual = records.find(feed.id)?.offlinePolicy,
        )

        records.updateOfflinePolicy(feedID = feed.id, policy = null)

        assertNull(records.find(feed.id)?.offlinePolicy)
    }

    @Test
    fun updateExcludeFromAi() = runTest {
        val records = FeedRecords(database)
        val feed = FeedFixture(database, records = records).create()

        assertFalse(feed.excludeFromAi)

        records.updateExcludeFromAi(feedID = feed.id, excluded = true)

        assertTrue(records.find(feed.id)?.excludeFromAi ?: false)

        records.updateExcludeFromAi(feedID = feed.id, excluded = false)

        assertFalse(records.find(feed.id)?.excludeFromAi ?: true)
    }
}
