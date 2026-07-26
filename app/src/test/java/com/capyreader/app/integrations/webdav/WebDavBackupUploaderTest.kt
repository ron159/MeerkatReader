package com.capyreader.app.integrations.webdav

import androidx.preference.PreferenceManager
import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.preferences.InMemorySecretStore
import com.capyreader.app.transfers.CapyBackupFile
import com.jocmp.capy.Account
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class WebDavBackupUploaderTest {
    private val context
        get() = RuntimeEnvironment.getApplication()
    private val account = mockk<Account>()
    private val backupFile = mockk<CapyBackupFile>()
    private val client = mockk<WebDavBackupClient>()
    private lateinit var appPreferences: AppPreferences
    private lateinit var uploader: WebDavBackupUploader

    @Before
    fun setUp() {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .clear()
            .commit()
        appPreferences = AppPreferences(context, InMemorySecretStore())
        uploader = WebDavBackupUploader(
            account = account,
            appPreferences = appPreferences,
            backupFile = backupFile,
            client = client,
        )
    }

    @Test
    fun `disabled upload leaves status and dependencies unchanged`() = runTest {
        appPreferences.webDavBackupOptions.lastBackupAt.set(100)
        appPreferences.webDavBackupOptions.lastError.set("Existing status")

        uploader.uploadNow(Instant.parse("2026-07-25T12:34:56Z")).getOrThrow()

        assertEquals(100, appPreferences.webDavBackupOptions.lastBackupAt.get())
        assertEquals("Existing status", appPreferences.webDavBackupOptions.lastError.get())
        coVerify(exactly = 0) { backupFile.createPayload(any(), any()) }
        coVerify(exactly = 0) { client.upload(any(), any()) }
    }

    @Test
    fun `enabled upload sends shared payload with deterministic filename`() = runTest {
        configure()
        val exportedAt = Instant.parse("2026-07-25T12:34:56Z")
        val payload = """{"version":2}""".encodeToByteArray()
        coEvery { backupFile.createPayload(account, exportedAt) } returns payload
        coEvery {
            client.upload("meerkat-backup-20260725-123456.json", payload)
        } returns Result.success(Unit)

        uploader.uploadNow(exportedAt).getOrThrow()

        assertEquals(
            exportedAt.epochSecond,
            appPreferences.webDavBackupOptions.lastBackupAt.get(),
        )
        assertEquals("", appPreferences.webDavBackupOptions.lastError.get())
        coVerify(exactly = 1) {
            client.upload("meerkat-backup-20260725-123456.json", payload)
        }
    }

    @Test
    fun `authentication failure stores a redacted visible error`() = runTest {
        configure()
        val exportedAt = Instant.parse("2026-07-25T12:34:56Z")
        val payload = byteArrayOf(1, 2, 3)
        coEvery { backupFile.createPayload(account, exportedAt) } returns payload
        coEvery { client.upload(any(), any()) } returns
            Result.failure(WebDavAuthenticationException())

        val result = uploader.uploadNow(exportedAt)

        assertTrue(result.isFailure)
        val visibleError = appPreferences.webDavBackupOptions.lastError.get()
        assertEquals("WebDAV authentication failed", visibleError)
        assertFalse(visibleError.contains("app-secret"))
    }

    private fun configure() {
        appPreferences.webDavBackupOptions.enabled.set(true)
        appPreferences.webDavBackupOptions.directoryUrl.set("https://dav.example/backups/")
        appPreferences.webDavBackupOptions.username.set("reader")
        appPreferences.webDavBackupOptions.password.set("app-secret")
    }
}
