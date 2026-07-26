package com.capyreader.app.common

import android.content.Context
import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.preferences.InMemorySecretStore
import com.jocmp.capy.AccountManager
import com.jocmp.capy.DatabaseProvider
import com.jocmp.capy.accounts.FaviconPolicy
import com.jocmp.capy.accounts.Source
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class SharedPreferenceStoreProviderTest {
    @JvmField
    @Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context
        get() = RuntimeEnvironment.getApplication()
    private lateinit var secretStore: InMemorySecretStore
    private lateinit var provider: SharedPreferenceStoreProvider

    @Before
    fun setUp() {
        ACCOUNT_IDS.forEach { accountID ->
            accountPreferences(accountID).edit().clear().commit()
        }
        secretStore = InMemorySecretStore()
        provider = SharedPreferenceStoreProvider(context, secretStore)
    }

    @Test
    fun `password persists through encrypted preference without plaintext`() {
        provider.build(ACCOUNT_ONE).password.set("account-secret")

        assertEquals(
            "account-secret",
            provider.build(ACCOUNT_ONE).password.get(),
        )
        assertEquals(
            "account-secret",
            secretStore.get(accountPasswordSecretKey(ACCOUNT_ONE)),
        )
        assertFalse(accountPreferences(ACCOUNT_ONE).contains("password"))
    }

    @Test
    fun `account creation writes credentials through encrypted preference`() {
        val manager = AccountManager(
            rootFolder = temporaryFolder.newFolder("accounts-root").toURI(),
            cacheDirectory = temporaryFolder.newFolder("accounts-cache").toURI(),
            databaseProvider = mockk<DatabaseProvider>(relaxed = true),
            preferenceStoreProvider = provider,
            faviconPolicy = mockk<FaviconPolicy>(),
            userAgent = { "TestUserAgent" },
            acceptLanguage = "en-US",
        )

        val accountID = manager.createAccount(
            username = "reader",
            password = "created-secret",
            url = "https://reader.example",
            source = Source.READER,
        )
        val preferences = provider.build(accountID)

        assertEquals("reader", preferences.username.get())
        assertEquals("created-secret", preferences.password.get())
        assertEquals("https://reader.example", preferences.url.get())
        assertFalse(accountPreferences(accountID).contains("password"))
        assertEquals(
            "created-secret",
            secretStore.get(accountPasswordSecretKey(accountID)),
        )
    }

    @Test
    fun `account credentials use isolated storage keys`() {
        provider.build(ACCOUNT_ONE).password.set("first-secret")
        provider.build(ACCOUNT_TWO).password.set("second-secret")

        assertEquals("first-secret", provider.build(ACCOUNT_ONE).password.get())
        assertEquals("second-secret", provider.build(ACCOUNT_TWO).password.get())
        assertFalse(
            accountPasswordSecretKey(ACCOUNT_ONE) ==
                accountPasswordSecretKey(ACCOUNT_TWO)
        )
    }

    @Test
    fun `first password read migrates legacy plaintext`() {
        accountPreferences(ACCOUNT_ONE)
            .edit()
            .putString("password", "legacy-secret")
            .commit()

        assertEquals(
            "legacy-secret",
            provider.build(ACCOUNT_ONE).password.get(),
        )
        assertEquals(
            "legacy-secret",
            secretStore.get(accountPasswordSecretKey(ACCOUNT_ONE)),
        )
        assertFalse(accountPreferences(ACCOUNT_ONE).contains("password"))
    }

    @Test
    fun `failed password migration keeps legacy plaintext recoverable`() {
        accountPreferences(ACCOUNT_ONE)
            .edit()
            .putString("password", "legacy-secret")
            .commit()
        secretStore.failWrites = true

        assertEquals(
            "legacy-secret",
            provider.build(ACCOUNT_ONE).password.get(),
        )
        assertTrue(accountPreferences(ACCOUNT_ONE).contains("password"))
        assertEquals(
            "legacy-secret",
            accountPreferences(ACCOUNT_ONE).getString("password", null),
        )
    }

    @Test
    fun `password update replaces encrypted value`() {
        val password = provider.build(ACCOUNT_ONE).password
        password.set("old-secret")

        password.set("new-secret")

        assertEquals("new-secret", provider.build(ACCOUNT_ONE).password.get())
        assertFalse(accountPreferences(ACCOUNT_ONE).contains("password"))
    }

    @Test
    fun `account deletion removes ordinary and encrypted preferences`() {
        val preferences = provider.build(ACCOUNT_ONE)
        preferences.username.set("reader")
        preferences.password.set("account-secret")

        provider.delete(ACCOUNT_ONE)

        assertTrue(accountPreferences(ACCOUNT_ONE).all.isEmpty())
        assertNull(secretStore.get(accountPasswordSecretKey(ACCOUNT_ONE)))
    }

    @Test
    fun `clearing app settings does not remove account credentials`() {
        provider.build(ACCOUNT_ONE).password.set("account-secret")
        val appSecretStore = InMemorySecretStore()
        AppPreferences(context, appSecretStore).also {
            it.aiOptions.apiKey.set("ai-secret")
            it.clearAll()
        }

        assertEquals(
            "account-secret",
            provider.build(ACCOUNT_ONE).password.get(),
        )
        assertNull(appSecretStore.get("ai_api_key"))
    }

    private fun accountPreferences(accountID: String) =
        context.getSharedPreferences(
            accountPreferencesName(accountID),
            Context.MODE_PRIVATE,
        )

    private companion object {
        const val ACCOUNT_ONE = "account-one"
        const val ACCOUNT_TWO = "account-two"
        val ACCOUNT_IDS = listOf(ACCOUNT_ONE, ACCOUNT_TWO)
    }
}
