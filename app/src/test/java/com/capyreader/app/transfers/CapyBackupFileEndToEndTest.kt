package com.capyreader.app.transfers

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.preference.PreferenceManager
import com.capyreader.app.R
import com.capyreader.app.common.AndroidDatabaseProvider
import com.capyreader.app.common.SharedPreferenceStoreProvider
import com.capyreader.app.common.accountPreferencesName
import com.capyreader.app.preferences.InMemorySecretStore
import com.jocmp.capy.Account
import com.jocmp.capy.AccountDelegate
import com.jocmp.capy.ArticleAutomationRule
import com.jocmp.capy.ArticleRuleAction
import com.jocmp.capy.ArticleRuleField
import com.jocmp.capy.Feed
import com.jocmp.capy.SavedSearchBackupEntry
import com.jocmp.capy.accounts.AddFeedResult
import com.jocmp.capy.accounts.FaviconPolicy
import com.jocmp.capy.accounts.Source
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.io.File
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class CapyBackupFileEndToEndTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    private lateinit var backupFile: CapyBackupFile
    private lateinit var databaseProvider: AndroidDatabaseProvider
    private lateinit var secretStore: InMemorySecretStore

    @Before
    fun setUp() {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .clear()
            .commit()
        backupFile = CapyBackupFile(context)
        databaseProvider = AndroidDatabaseProvider(context)
        secretStore = InMemorySecretStore()
    }

    @Test
    fun `version 2 export is portable and preview is read only`() = runTest {
        val fixture = createExportFixture()
        val root = fixture.document()

        assertEquals(2, root.required("version").jsonPrimitive.int)
        Instant.parse(root.required("exportedAt").jsonPrimitive.content)

        val appPreferences = root
            .required("app")
            .jsonObject
            .required("preferences")
            .jsonObject
        assertTrue(PORTABLE_APP_KEY in appPreferences)
        assertTrue(AI_ENABLED_KEY in appPreferences)
        assertTrue(AI_MODEL_KEY in appPreferences)
        assertFalse(AI_API_KEY in appPreferences)
        assertFalse(WALLABAG_ACCESS_TOKEN_KEY in appPreferences)
        assertFalse(WEB_DAV_ENABLED_KEY in appPreferences)
        assertFalse(WEB_DAV_DIRECTORY_URL_KEY in appPreferences)
        assertFalse(WEB_DAV_USERNAME_KEY in appPreferences)
        assertFalse(WEB_DAV_PASSWORD_KEY in appPreferences)
        assertFalse(WEB_DAV_LAST_AT_KEY in appPreferences)
        assertFalse(WEB_DAV_LAST_ERROR_KEY in appPreferences)
        assertFalse(AUTOMATIC_BACKUP_TREE_URI_KEY in appPreferences)
        assertFalse(BACKGROUND_AI_DAILY_USAGE_KEY in appPreferences)

        val account = root.required("account").jsonObject
        assertEquals(Source.FEEDBIN.name, account.required("source").jsonPrimitive.content)
        assertTrue(
            account.required("subscriptionsOpml")
                .jsonPrimitive
                .content
                .contains(SOURCE_FEED_URL)
        )
        val accountPreferences = account.required("preferences").jsonObject
        assertTrue(SOURCE_ACCOUNT_ONLY_KEY in accountPreferences)
        assertFalse(PASSWORD_KEY in accountPreferences)

        val rules = root.required("rules").jsonArray
        assertEquals(1, rules.size)
        assertEquals(SOURCE_RULE_ID, rules.single().jsonObject.required("id").jsonPrimitive.content)

        val savedSearch = root.required("savedSearches").jsonArray.single().jsonObject
        assertEquals(SOURCE_SAVED_SEARCH_ID, savedSearch.required("id").jsonPrimitive.content)
        assertEquals(SOURCE_SAVED_SEARCH_NAME, savedSearch.required("name").jsonPrimitive.content)
        assertEquals(SOURCE_SAVED_SEARCH_QUERY, savedSearch.required("query").jsonPrimitive.content)
        assertFalse(savedSearch.required("showUnreadBadge").jsonPrimitive.boolean)
        assertEquals(
            listOf(SOURCE_ARTICLE_ID),
            savedSearch.required("articleIDs").jsonArray.map { it.jsonPrimitive.content },
        )

        val readLater = root.required("readLater").jsonArray.single().jsonObject
        assertEquals(SOURCE_ARTICLE_ID, readLater.required("id").jsonPrimitive.content)
        assertEquals(SOURCE_ARTICLE_URL, readLater.required("url").jsonPrimitive.content)
        assertEquals(
            listOf(SOURCE_ARTICLE_ID),
            root.required("starred").jsonArray.map { it.jsonPrimitive.content },
        )

        val ai = root.required("ai").jsonObject
        assertFalse(ai["includeApiKey"]?.jsonPrimitive?.boolean ?: false)
        val aiSettings = ai.required("settings").jsonObject
        assertTrue(AI_ENABLED_KEY in aiSettings)
        assertTrue(AI_MODEL_KEY in aiSettings)
        assertFalse(AI_API_KEY in aiSettings)
        assertFalse(BACKGROUND_AI_DAILY_USAGE_KEY in aiSettings)

        val target = createTargetFixture(Source.FEEDBIN)
        val appBefore = defaultPreferences().all.toMap()
        val accountBefore = accountPreferences(target.account).all.toMap()
        val searchesBefore = target.account.savedSearchBackupEntries()
        val starsBefore = target.account.starredArticleBackupIDs()

        val preview = backupFile.restorePreview(target.account, fixture.uri)

        assertNotNull(preview)
        preview!!
        assertEquals(2, preview.version)
        assertEquals(Source.FEEDBIN, preview.source)
        assertTrue(preview.hasSubscriptions)
        assertEquals(1, preview.savedSearchCount)
        assertEquals(1, preview.readLaterCount)
        assertEquals(1, preview.starredCount)
        assertTrue(preview.hasRules)
        assertTrue(preview.hasAiSettings)
        assertEquals(appBefore, defaultPreferences().all)
        assertEquals(accountBefore, accountPreferences(target.account).all)
        assertEquals(searchesBefore, target.account.savedSearchBackupEntries())
        assertEquals(starsBefore, target.account.starredArticleBackupIDs())
        verifyNoRestoreCalls(target.delegate)
    }

    @Test
    fun `webdav payload is the same version 2 document used by manual export`() = runTest {
        val fixture = createExportFixture()
        val manualDocument = fixture.document()
        val exportedAt = Instant.parse(
            manualDocument.required("exportedAt").jsonPrimitive.content
        )

        val webDavDocument = Json.parseToJsonElement(
            backupFile.createPayload(fixture.account, exportedAt).decodeToString()
        )

        assertEquals(manualDocument, webDavDocument)
    }

    @Test
    fun `preview rejects unsupported version and source mismatch without mutation`() = runTest {
        val fixture = createExportFixture()
        val target = createTargetFixture(Source.FEEDBIN)
        val originalDocument = fixture.file.readText()
        val appBefore = defaultPreferences().all.toMap()
        val accountBefore = accountPreferences(target.account).all.toMap()

        fixture.file.writeText(originalDocument.withVersion(99))

        assertNull(backupFile.restorePreview(target.account, fixture.uri))
        assertEquals(
            context.getString(R.string.backup_importer_failure),
            ShadowToast.getTextOfLatestToast(),
        )
        assertEquals(appBefore, defaultPreferences().all)
        assertEquals(accountBefore, accountPreferences(target.account).all)
        verifyNoRestoreCalls(target.delegate)

        fixture.file.writeText(originalDocument)
        val mismatched = createTargetFixture(Source.LOCAL)

        assertNull(backupFile.restorePreview(mismatched.account, fixture.uri))
        assertEquals(
            context.getString(R.string.backup_importer_source_mismatch),
            ShadowToast.getTextOfLatestToast(),
        )
        verifyNoRestoreCalls(mismatched.delegate)
    }

    @Test
    fun `merge restores portable state and preserves unrelated settings`() = runTest {
        assertRestore(
            mode = BackupRestoreMode.MERGE,
            preservesUnrelatedSettings = true,
        )
    }

    @Test
    fun `replace restores portable state and removes unrelated settings`() = runTest {
        assertRestore(
            mode = BackupRestoreMode.REPLACE,
            preservesUnrelatedSettings = false,
        )
    }

    private suspend fun assertRestore(
        mode: BackupRestoreMode,
        preservesUnrelatedSettings: Boolean,
    ) {
        val fixture = createExportFixture()
        val target = createTargetFixture(Source.FEEDBIN)

        backupFile.restore(target.account, fixture.uri, mode)

        val appPreferences = defaultPreferences()
        assertEquals(SOURCE_PORTABLE_APP_VALUE, appPreferences.getString(PORTABLE_APP_KEY, null))
        assertTrue(appPreferences.getBoolean(AI_ENABLED_KEY, false))
        assertEquals(SOURCE_AI_MODEL, appPreferences.getString(AI_MODEL_KEY, null))
        assertEquals(target.account.id, appPreferences.getString(ACCOUNT_ID_KEY, null))
        assertEquals(TARGET_API_KEY, appPreferences.getString(AI_API_KEY, null))
        assertEquals(
            TARGET_WALLABAG_ACCESS_TOKEN,
            appPreferences.getString(WALLABAG_ACCESS_TOKEN_KEY, null),
        )
        assertFalse(appPreferences.getBoolean(WEB_DAV_ENABLED_KEY, true))
        assertEquals(
            TARGET_WEB_DAV_DIRECTORY_URL,
            appPreferences.getString(WEB_DAV_DIRECTORY_URL_KEY, null),
        )
        assertEquals(
            TARGET_WEB_DAV_USERNAME,
            appPreferences.getString(WEB_DAV_USERNAME_KEY, null),
        )
        assertEquals(
            TARGET_WEB_DAV_PASSWORD,
            appPreferences.getString(WEB_DAV_PASSWORD_KEY, null),
        )
        assertEquals(
            TARGET_WEB_DAV_LAST_AT,
            appPreferences.getLong(WEB_DAV_LAST_AT_KEY, 0L),
        )
        assertEquals(
            TARGET_WEB_DAV_LAST_ERROR,
            appPreferences.getString(WEB_DAV_LAST_ERROR_KEY, null),
        )
        assertEquals(
            TARGET_AUTOMATIC_BACKUP_TREE_URI,
            appPreferences.getString(AUTOMATIC_BACKUP_TREE_URI_KEY, null),
        )
        assertEquals(
            TARGET_BACKGROUND_AI_DAILY_USAGE,
            appPreferences.getString(BACKGROUND_AI_DAILY_USAGE_KEY, null),
        )
        assertFalse(appPreferences.getBoolean(AUTOMATIC_BACKUP_ENABLED_KEY, true))
        assertEquals(
            preservesUnrelatedSettings,
            appPreferences.contains(TARGET_APP_ONLY_KEY),
        )

        val accountPreferences = accountPreferences(target.account)
        assertEquals(SOURCE_USERNAME, target.account.preferences.username.get())
        assertEquals(TARGET_PASSWORD, target.account.preferences.password.get())
        assertEquals(
            TARGET_LEGACY_PASSWORD,
            accountPreferences.getString(PASSWORD_KEY, null),
        )
        assertTrue(accountPreferences.contains(SOURCE_ACCOUNT_ONLY_KEY))
        assertEquals(
            preservesUnrelatedSettings,
            accountPreferences.contains(TARGET_ACCOUNT_ONLY_KEY),
        )
        assertEquals(listOf(SOURCE_RULE), target.account.preferences.automationRules.get())

        val savedSearches = target.account.savedSearchBackupEntries().associateBy { it.id }
        assertEquals(setOf(TARGET_SAVED_SEARCH_ID, SOURCE_SAVED_SEARCH_ID), savedSearches.keys)
        assertEquals(
            SavedSearchBackupEntry(
                id = SOURCE_SAVED_SEARCH_ID,
                name = SOURCE_SAVED_SEARCH_NAME,
                query = SOURCE_SAVED_SEARCH_QUERY,
                showUnreadBadge = false,
                articleIDs = listOf(SOURCE_ARTICLE_ID),
            ),
            savedSearches[SOURCE_SAVED_SEARCH_ID],
        )
        assertEquals(
            setOf(TARGET_STARRED_ARTICLE_ID, SOURCE_ARTICLE_ID),
            target.account.starredArticleBackupIDs().toSet(),
        )

        coVerify(exactly = 1) {
            target.delegate.addFeed(
                url = SOURCE_FEED_URL,
                title = SOURCE_FEED_TITLE,
                folderTitles = emptyList(),
            )
        }
        coVerify(exactly = 1) {
            target.delegate.createPage(SOURCE_ARTICLE_URL)
        }
        coVerify(exactly = 1) {
            target.delegate.addStar(listOf(SOURCE_ARTICLE_ID))
        }
        coVerify(exactly = 1) {
            target.delegate.refresh(any(), any())
        }
    }

    private suspend fun createExportFixture(): ExportFixture {
        val source = createAccount(
            idPrefix = "backup-source",
            source = Source.FEEDBIN,
            delegate = mockk(),
        )
        val appPreferences = defaultPreferences()
        appPreferences.edit()
            .putString(PORTABLE_APP_KEY, SOURCE_PORTABLE_APP_VALUE)
            .putBoolean(AI_ENABLED_KEY, true)
            .putString(AI_MODEL_KEY, SOURCE_AI_MODEL)
            .putString(AI_API_KEY, SOURCE_API_KEY)
            .putString(WALLABAG_ACCESS_TOKEN_KEY, SOURCE_WALLABAG_ACCESS_TOKEN)
            .putBoolean(WEB_DAV_ENABLED_KEY, true)
            .putString(WEB_DAV_DIRECTORY_URL_KEY, SOURCE_WEB_DAV_DIRECTORY_URL)
            .putString(WEB_DAV_USERNAME_KEY, SOURCE_WEB_DAV_USERNAME)
            .putString(WEB_DAV_PASSWORD_KEY, SOURCE_WEB_DAV_PASSWORD)
            .putLong(WEB_DAV_LAST_AT_KEY, SOURCE_WEB_DAV_LAST_AT)
            .putString(WEB_DAV_LAST_ERROR_KEY, SOURCE_WEB_DAV_LAST_ERROR)
            .putString(AUTOMATIC_BACKUP_TREE_URI_KEY, SOURCE_AUTOMATIC_BACKUP_TREE_URI)
            .putString(BACKGROUND_AI_DAILY_USAGE_KEY, SOURCE_BACKGROUND_AI_DAILY_USAGE)
            .putBoolean(AUTOMATIC_BACKUP_ENABLED_KEY, true)
            .putString(ACCOUNT_ID_KEY, source.id)
            .commit()

        source.preferences.username.set(SOURCE_USERNAME)
        source.preferences.password.set(SOURCE_PASSWORD)
        source.preferences.automationRules.set(listOf(SOURCE_RULE))
        accountPreferences(source).edit()
            .putString(PASSWORD_KEY, SOURCE_LEGACY_PASSWORD)
            .putBoolean(SOURCE_ACCOUNT_ONLY_KEY, true)
            .commit()

        seedArticle(
            account = source,
            articleID = SOURCE_ARTICLE_ID,
            articleURL = SOURCE_ARTICLE_URL,
            feedID = SOURCE_FEED_ID,
            feedURL = SOURCE_FEED_URL,
            feedTitle = SOURCE_FEED_TITLE,
            readLater = true,
            starred = true,
        )
        source.restoreSavedSearchBackupEntries(
            listOf(
                SavedSearchBackupEntry(
                    id = SOURCE_SAVED_SEARCH_ID,
                    name = SOURCE_SAVED_SEARCH_NAME,
                    query = SOURCE_SAVED_SEARCH_QUERY,
                    showUnreadBadge = false,
                    articleIDs = listOf(SOURCE_ARTICLE_ID),
                )
            )
        )

        val file = temporaryFolder.newFile("backup-${UUID.randomUUID()}.json")
        val uri = Uri.fromFile(file)
        backupFile.export(source, uri)
        assertTrue(file.length() > 0)

        return ExportFixture(file = file, uri = uri, account = source)
    }

    private suspend fun createTargetFixture(source: Source): TargetFixture {
        defaultPreferences().edit()
            .clear()
            .putString(PORTABLE_APP_KEY, TARGET_PORTABLE_APP_VALUE)
            .putString(TARGET_APP_ONLY_KEY, TARGET_APP_ONLY_VALUE)
            .putString(AI_API_KEY, TARGET_API_KEY)
            .putString(WALLABAG_ACCESS_TOKEN_KEY, TARGET_WALLABAG_ACCESS_TOKEN)
            .putBoolean(WEB_DAV_ENABLED_KEY, false)
            .putString(WEB_DAV_DIRECTORY_URL_KEY, TARGET_WEB_DAV_DIRECTORY_URL)
            .putString(WEB_DAV_USERNAME_KEY, TARGET_WEB_DAV_USERNAME)
            .putString(WEB_DAV_PASSWORD_KEY, TARGET_WEB_DAV_PASSWORD)
            .putLong(WEB_DAV_LAST_AT_KEY, TARGET_WEB_DAV_LAST_AT)
            .putString(WEB_DAV_LAST_ERROR_KEY, TARGET_WEB_DAV_LAST_ERROR)
            .putString(AUTOMATIC_BACKUP_TREE_URI_KEY, TARGET_AUTOMATIC_BACKUP_TREE_URI)
            .putString(BACKGROUND_AI_DAILY_USAGE_KEY, TARGET_BACKGROUND_AI_DAILY_USAGE)
            .putBoolean(AUTOMATIC_BACKUP_ENABLED_KEY, false)
            .commit()

        val delegate = mockk<AccountDelegate>()
        coEvery {
            delegate.addFeed(any(), any(), any())
        } returns AddFeedResult.Success(
            Feed(
                id = "imported-feed",
                subscriptionID = "imported-feed",
                title = "Imported Feed",
                feedURL = SOURCE_FEED_URL,
            )
        )
        coEvery { delegate.createPage(any()) } returns Result.success(Unit)
        coEvery { delegate.addStar(any()) } returns Result.success(Unit)
        coEvery { delegate.refresh(any(), any()) } returns Result.success(Unit)

        val target = createAccount(
            idPrefix = "backup-target",
            source = source,
            delegate = delegate,
        )
        defaultPreferences().edit()
            .putString(ACCOUNT_ID_KEY, target.id)
            .commit()
        target.preferences.username.set(TARGET_USERNAME)
        target.preferences.password.set(TARGET_PASSWORD)
        accountPreferences(target).edit()
            .putString(PASSWORD_KEY, TARGET_LEGACY_PASSWORD)
            .putBoolean(TARGET_ACCOUNT_ONLY_KEY, true)
            .commit()

        seedArticle(
            account = target,
            articleID = SOURCE_ARTICLE_ID,
            articleURL = SOURCE_ARTICLE_URL,
            feedID = TARGET_FEED_ID,
            feedURL = TARGET_FEED_URL,
            feedTitle = TARGET_FEED_TITLE,
            readLater = false,
            starred = false,
        )
        seedArticle(
            account = target,
            articleID = TARGET_STARRED_ARTICLE_ID,
            articleURL = TARGET_STARRED_ARTICLE_URL,
            feedID = TARGET_FEED_ID,
            feedURL = TARGET_FEED_URL,
            feedTitle = TARGET_FEED_TITLE,
            readLater = false,
            starred = true,
        )
        target.restoreSavedSearchBackupEntries(
            listOf(
                SavedSearchBackupEntry(
                    id = TARGET_SAVED_SEARCH_ID,
                    name = TARGET_SAVED_SEARCH_NAME,
                    query = TARGET_SAVED_SEARCH_QUERY,
                    showUnreadBadge = true,
                    articleIDs = listOf(TARGET_STARRED_ARTICLE_ID),
                )
            )
        )

        return TargetFixture(account = target, delegate = delegate)
    }

    private fun createAccount(
        idPrefix: String,
        source: Source,
        delegate: AccountDelegate,
    ): Account {
        val id = "$idPrefix-${UUID.randomUUID()}"
        return Account(
            id = id,
            path = temporaryFolder.newFolder(id).toURI(),
            cacheDirectory = temporaryFolder.newFolder("$id-cache").toURI(),
            database = databaseProvider.build(id),
            preferences = SharedPreferenceStoreProvider(context, secretStore).build(id),
            source = source,
            faviconPolicy = FaviconPolicy { true },
            userAgent = { "BackupTest/1.0" },
            acceptLanguage = "en-US",
            delegate = delegate,
        )
    }

    private fun seedArticle(
        account: Account,
        articleID: String,
        articleURL: String,
        feedID: String,
        feedURL: String,
        feedTitle: String,
        readLater: Boolean,
        starred: Boolean,
    ) {
        val publishedAt = Instant.parse("2026-07-25T00:00:00Z").epochSecond
        account.database.transaction {
            account.database.feedsQueries.upsert(
                id = feedID,
                subscription_id = feedID,
                title = feedTitle,
                feed_url = feedURL,
                site_url = feedURL,
                favicon_url = null,
                priority = null,
                itunes_image_url = null,
                read_later = readLater,
            )
            account.database.articlesQueries.create(
                id = articleID,
                feed_id = feedID,
                title = "Article $articleID",
                author = "Backup Test",
                content_html = "<p>Backup content</p>",
                extracted_content_url = null,
                url = articleURL,
                summary = "Backup summary",
                image_url = null,
                published_at = publishedAt,
                enclosure_type = null,
            )
            account.database.articlesQueries.createStatus(
                article_id = articleID,
                updated_at = publishedAt,
                read = false,
            )
            if (starred) {
                account.database.articlesQueries.markStarred(
                    articleID = articleID,
                    starred = true,
                    lastUnstarredAt = null,
                )
            }
        }
    }

    private fun verifyNoRestoreCalls(delegate: AccountDelegate) {
        coVerify(exactly = 0) { delegate.addFeed(any(), any(), any()) }
        coVerify(exactly = 0) { delegate.createPage(any()) }
        coVerify(exactly = 0) { delegate.addStar(any()) }
        coVerify(exactly = 0) { delegate.refresh(any(), any()) }
    }

    private fun defaultPreferences(): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    private fun accountPreferences(account: Account): SharedPreferences {
        return context.getSharedPreferences(
            accountPreferencesName(account.id),
            Context.MODE_PRIVATE,
        )
    }

    private fun ExportFixture.document(): JsonObject {
        return Json.parseToJsonElement(file.readText()).jsonObject
    }

    private fun JsonObject.required(key: String) = requireNotNull(this[key]) {
        "Missing JSON key: $key"
    }

    private fun String.withVersion(version: Int): String {
        val root = Json.parseToJsonElement(this).jsonObject
        return JsonObject(root + ("version" to JsonPrimitive(version))).toString()
    }

    private data class ExportFixture(
        val file: File,
        val uri: Uri,
        val account: Account,
    )

    private data class TargetFixture(
        val account: Account,
        val delegate: AccountDelegate,
    )

    companion object {
        private const val ACCOUNT_ID_KEY = "account_id"
        private const val PASSWORD_KEY = "password"
        private const val PORTABLE_APP_KEY = "portable_app_value"
        private const val SOURCE_PORTABLE_APP_VALUE = "from-backup"
        private const val TARGET_PORTABLE_APP_VALUE = "before-restore"
        private const val TARGET_APP_ONLY_KEY = "target_app_only"
        private const val TARGET_APP_ONLY_VALUE = "keep-in-merge"
        private const val SOURCE_ACCOUNT_ONLY_KEY = "source_account_only"
        private const val TARGET_ACCOUNT_ONLY_KEY = "target_account_only"
        private const val AI_ENABLED_KEY = "ai_enabled"
        private const val AI_MODEL_KEY = "ai_model"
        private const val AI_API_KEY = "ai_api_key"
        private const val WALLABAG_ACCESS_TOKEN_KEY = "wallabag_access_token"
        private const val WEB_DAV_ENABLED_KEY = "webdav_backup_enabled"
        private const val WEB_DAV_DIRECTORY_URL_KEY = "webdav_backup_directory_url"
        private const val WEB_DAV_USERNAME_KEY = "webdav_backup_username"
        private const val WEB_DAV_PASSWORD_KEY = "webdav_backup_password"
        private const val WEB_DAV_LAST_AT_KEY = "webdav_backup_last_at"
        private const val WEB_DAV_LAST_ERROR_KEY = "webdav_backup_last_error"
        private const val AUTOMATIC_BACKUP_ENABLED_KEY = "automatic_backup_enabled"
        private const val AUTOMATIC_BACKUP_TREE_URI_KEY = "automatic_backup_tree_uri"
        private const val BACKGROUND_AI_DAILY_USAGE_KEY =
            "ai_background_preview_summaries_daily_usage"

        private const val SOURCE_AI_MODEL = "backup-model"
        private const val SOURCE_API_KEY = "source-api-secret"
        private const val SOURCE_WALLABAG_ACCESS_TOKEN = "source-wallabag-secret"
        private const val SOURCE_WEB_DAV_DIRECTORY_URL = "https://source.example/backups/"
        private const val SOURCE_WEB_DAV_USERNAME = "source-webdav-user"
        private const val SOURCE_WEB_DAV_PASSWORD = "source-webdav-secret"
        private const val SOURCE_WEB_DAV_LAST_AT = 1_753_444_496L
        private const val SOURCE_WEB_DAV_LAST_ERROR = "source webdav error"
        private const val SOURCE_AUTOMATIC_BACKUP_TREE_URI = "content://source-backups"
        private const val SOURCE_BACKGROUND_AI_DAILY_USAGE = "2026-07-25|7"
        private const val TARGET_API_KEY = "target-api-secret"
        private const val TARGET_WALLABAG_ACCESS_TOKEN = "target-wallabag-secret"
        private const val TARGET_WEB_DAV_DIRECTORY_URL = "https://target.example/backups/"
        private const val TARGET_WEB_DAV_USERNAME = "target-webdav-user"
        private const val TARGET_WEB_DAV_PASSWORD = "target-webdav-secret"
        private const val TARGET_WEB_DAV_LAST_AT = 1_753_444_400L
        private const val TARGET_WEB_DAV_LAST_ERROR = "target webdav error"
        private const val TARGET_AUTOMATIC_BACKUP_TREE_URI = "content://target-backups"
        private const val TARGET_BACKGROUND_AI_DAILY_USAGE = "2026-07-25|3"

        private const val SOURCE_USERNAME = "source-user"
        private const val TARGET_USERNAME = "target-user"
        private const val SOURCE_PASSWORD = "source-secret"
        private const val SOURCE_LEGACY_PASSWORD = "source-legacy-secret"
        private const val TARGET_PASSWORD = "target-secret"
        private const val TARGET_LEGACY_PASSWORD = "target-legacy-secret"

        private const val SOURCE_FEED_ID = "source-feed"
        private const val SOURCE_FEED_TITLE = "Read Later Feed"
        private const val SOURCE_FEED_URL = "https://example.com/source-feed.xml"
        private const val TARGET_FEED_ID = "target-feed"
        private const val TARGET_FEED_TITLE = "Target Feed"
        private const val TARGET_FEED_URL = "https://example.com/target-feed.xml"
        private const val SOURCE_ARTICLE_ID = "source-article"
        private const val SOURCE_ARTICLE_URL = "https://example.com/source-article"
        private const val TARGET_STARRED_ARTICLE_ID = "target-starred-article"
        private const val TARGET_STARRED_ARTICLE_URL =
            "https://example.com/target-starred-article"

        private const val SOURCE_SAVED_SEARCH_ID = "saved-search-source"
        private const val SOURCE_SAVED_SEARCH_NAME = "Backup Search"
        private const val SOURCE_SAVED_SEARCH_QUERY = "title:backup"
        private const val TARGET_SAVED_SEARCH_ID = "saved-search-target"
        private const val TARGET_SAVED_SEARCH_NAME = "Existing Search"
        private const val TARGET_SAVED_SEARCH_QUERY = "title:existing"

        private const val SOURCE_RULE_ID = "rule-source"
        private val SOURCE_RULE = ArticleAutomationRule(
            id = SOURCE_RULE_ID,
            name = "Backup rule",
            field = ArticleRuleField.TITLE,
            pattern = "important",
            actions = setOf(ArticleRuleAction.STAR),
        )
    }
}
