package com.jocmp.capy.opml

import com.jocmp.capy.Account
import com.jocmp.capy.AccountPreferences
import com.jocmp.capy.InMemoryDataStore
import com.jocmp.capy.InMemoryDatabaseProvider
import com.jocmp.capy.MockFeedFinder
import com.jocmp.capy.accounts.local.LocalAccountDelegate
import com.jocmp.capy.db.Database
import com.jocmp.capy.fixtures.AccountFixture
import com.jocmp.capy.fixtures.FeedFixture
import com.jocmp.capy.fixtures.GenericFeed
import com.jocmp.capy.testFile
import com.jocmp.feedfinder.parser.Feed
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals

class OPMLImporterTest {
    @JvmField
    @Rule
    val folder = TemporaryFolder()

    private val accountID = "777"
    private lateinit var account: Account
    private lateinit var database: Database

    private val httpClient = mockk<OkHttpClient>()

    private val sites = listOf<Feed>(
        GenericFeed(name = "Daring Fireball", url = "https://daringfireball.net/feeds/main"),
        GenericFeed(
            name = "BBC News - World",
            url = "https://feeds.bbci.co.uk/news/world/rss.xml"
        ),
        GenericFeed(name = "NetNewsWire", url = "https://netnewswire.blog/feed.xml"),
        GenericFeed(name = "Block Club Chicago", url = "https://blockclubchicago.org/feed/"),
        GenericFeed(name = "Julia Evans", url = "https://jvns.ca/atom.xml"),
    ).associateBy { it.feedURL.toString() }

    private val finder = MockFeedFinder(sites)

    @Before
    fun setup() {
        database = InMemoryDatabaseProvider.build(accountID)
        val delegate = LocalAccountDelegate(
            database = database,
            httpClient = httpClient,
            feedFinder = finder,
            preferences = AccountPreferences(InMemoryDataStore()),
        )

        account = AccountFixture.create(
            id = accountID,
            database = database,
            parentFolder = folder,
            accountDelegate = delegate
        )
    }

    @Test
    fun `it imports feeds and folders`() = runTest {
        val inputStream = testFile("nested_import.xml").inputStream()

        OPMLImporter(account).import(inputStream = inputStream)

        val topLevelFeeds = account.feeds.first().map { it.title }.toSet()
        val newsFeeds = account.folders.first().first().feeds.map { it.title }.toSet()

        assertEquals(expected = setOf("Daring Fireball", "Julia Evans"), actual = topLevelFeeds)
        assertEquals(
            expected = setOf("BBC News - World", "NetNewsWire", "Block Club Chicago"),
            actual = newsFeeds
        )
    }

    @Test
    fun `it handles feeds nested in multiple folders`() = runTest {
        val inputStream = testFile("multiple_matching_feeds.xml").inputStream()

        OPMLImporter(account).import(inputStream = inputStream)

        val topLevelFeeds = account.feeds.first().map { it.title }.toSet()
        val folders = account.folders.first()
        val appleFeeds = folders.find { it.title == "Apple" }!!.feeds.map { it.title }.toSet()
        val blogFeeds = folders.find { it.title == "Blogs" }!!.feeds.map { it.title }.toSet()
        val newsFeeds = folders.find { it.title == "News" }!!.feeds.map { it.title }.toSet()

        assertEquals(expected = setOf("Julia Evans"), actual = topLevelFeeds)
        assertEquals(expected = setOf("Daring Fireball"), actual = blogFeeds)
        assertEquals(expected = setOf("Daring Fireball"), actual = appleFeeds)
        assertEquals(
            expected = setOf("BBC News - World", "NetNewsWire", "Block Club Chicago"),
            actual = newsFeeds
        )
    }

    @Test
    fun `it skips existing feeds while importing missing feeds and folders`() = runTest {
        val existingFeed = FeedFixture(database).create(
            feedID = "existing-subscription",
            feedURL = "https://jvns.ca/atom.xml",
            title = "My Julia Evans subscription",
            folderNames = listOf("Saved"),
        )

        account.import(
            inputStream = testFile("multiple_matching_feeds.xml").inputStream(),
            skipExistingFeeds = true,
        ) {}

        val feeds = account.allFeeds.first()
        val restoredFeed = feeds.single { it.feedURL == existingFeed.feedURL }
        val folders = account.folders.first().associate { folder ->
            folder.title to folder.feeds.map { it.title }.toSet()
        }

        assertEquals(expected = 5, actual = feeds.size)
        assertEquals(expected = existingFeed.id, actual = restoredFeed.id)
        assertEquals(expected = existingFeed.title, actual = restoredFeed.title)
        assertEquals(
            expected = mapOf(
                "Saved" to setOf(existingFeed.title),
                "Apple" to setOf("Daring Fireball"),
                "Blogs" to setOf("Daring Fireball"),
                "News" to setOf("BBC News - World", "NetNewsWire", "Block Club Chicago"),
            ),
            actual = folders,
        )
    }

    @Test
    fun `it reports completed progress when all feeds are skipped`() = runTest {
        val fixture = FeedFixture(database)
        sites.values.forEach { site ->
            fixture.create(
                feedURL = site.feedURL.toString(),
                title = site.name,
                folderNames = listOf("Saved"),
            )
        }
        val progress = mutableListOf<ImportProgress>()
        val existingAccount = account.copy(delegate = mockk())

        OPMLImporter(existingAccount).import(
            inputStream = testFile("multiple_matching_feeds.xml").inputStream(),
            skipExistingFeeds = true,
            onProgress = { progress.add(it) },
        )

        assertEquals(
            expected = (0..sites.size).map { ImportProgress(currentCount = it, total = sites.size) },
            actual = progress,
        )
        assertEquals(expected = 1f, actual = progress.last().percent)
    }

    @Test
    fun `it updates existing feeds and folders by default`() = runTest {
        val feedURL = "https://daringfireball.net/feeds/main"
        val existingFeed = FeedFixture(database).create(
            feedID = feedURL,
            feedURL = feedURL,
            title = "Previous title",
        )

        account.import(testFile("multiple_matching_feeds.xml").inputStream()) {}

        val feeds = account.allFeeds.first()
        val importedFeed = feeds.single { it.id == existingFeed.id }
        val importedFolders = account.folders.first().filter { folder ->
            folder.feeds.any { it.id == existingFeed.id }
        }.map { it.title }.toSet()

        assertEquals(expected = 5, actual = feeds.size)
        assertEquals(expected = "Daring Fireball", actual = importedFeed.title)
        assertEquals(expected = setOf("Apple", "Blogs"), actual = importedFolders)
    }

    @Test
    fun `it handles invalid https URLs`() = runTest {
        val inputStream = testFile("newsblur.xml").inputStream()

        OPMLImporter(account).import(inputStream = inputStream)

        val topLevelFeeds = account.feeds.first().map { it.title }.toSet()

        assertEquals(expected = setOf("Daring Fireball"), actual = topLevelFeeds)
    }
}
