package com.capyreader.app.integrations.webdav

import androidx.preference.PreferenceManager
import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.preferences.InMemorySecretStore
import kotlinx.coroutines.test.runTest
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class WebDavBackupClientTest {
    private val context
        get() = RuntimeEnvironment.getApplication()
    private lateinit var appPreferences: AppPreferences

    @Before
    fun setUp() {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .clear()
            .commit()
        appPreferences = AppPreferences(context, InMemorySecretStore()).also {
            it.webDavBackupOptions.directoryUrl.set(
                "https://dav.example/remote.php/dav/files/reader/Backups/"
            )
            it.webDavBackupOptions.username.set("reader@example.com")
            it.webDavBackupOptions.password.set("app-secret")
        }
    }

    @Test
    fun `connection test uses authenticated depth zero PROPFIND`() = runTest {
        val interceptor = WebDavResponseInterceptor(code = 207)

        client(interceptor).testConnection().getOrThrow()

        assertEquals("PROPFIND", interceptor.request.method)
        assertEquals("0", interceptor.request.header("Depth"))
        assertEquals(
            Credentials.basic("reader@example.com", "app-secret", Charsets.UTF_8),
            interceptor.request.header("Authorization"),
        )
        assertEquals(
            "https://dav.example/remote.php/dav/files/reader/Backups/",
            interceptor.request.url.toString(),
        )
    }

    @Test
    fun `upload encodes one filename segment and sends exact backup payload`() = runTest {
        val interceptor = WebDavResponseInterceptor(code = 201)
        val payload = """{"version":2,"exportedAt":"2026-07-25T00:00:00Z"}"""
            .encodeToByteArray()

        client(interceptor).upload(
            fileName = "meerkat/backup 1.json",
            payload = payload,
        ).getOrThrow()

        assertEquals("PUT", interceptor.request.method)
        assertEquals(
            "https://dav.example/remote.php/dav/files/reader/Backups/" +
                "meerkat%2Fbackup%201.json",
            interceptor.request.url.toString(),
        )
        assertEquals(
            "application/json; charset=utf-8",
            interceptor.request.body?.contentType().toString(),
        )
        val body = Buffer()
        requireNotNull(interceptor.request.body).writeTo(body)
        assertTrue(payload.contentEquals(body.readByteArray()))
    }

    @Test
    fun `authentication failure is permanent and redacts credentials`() = runTest {
        val error = client(WebDavResponseInterceptor(code = 401))
            .testConnection()
            .exceptionOrNull()

        assertTrue(error is WebDavAuthenticationException)
        assertFalse((error as WebDavBackupException).retryable)
        assertFalse(error.message.orEmpty().contains("app-secret"))
        assertFalse(error.message.orEmpty().contains("reader@example.com"))
    }

    @Test
    fun `server failure is retryable`() = runTest {
        val error = client(WebDavResponseInterceptor(code = 503))
            .upload("backup.json", byteArrayOf())
            .exceptionOrNull()

        assertTrue(error is WebDavHttpException)
        assertTrue((error as WebDavBackupException).retryable)
    }

    @Test
    fun `credentials embedded in directory URL are rejected`() = runTest {
        appPreferences.webDavBackupOptions.directoryUrl.set(
            "https://embedded:secret@dav.example/backups/"
        )

        val error = client(WebDavResponseInterceptor(code = 207))
            .testConnection()
            .exceptionOrNull()

        assertTrue(error is WebDavConfigurationException)
        assertFalse(error?.message.orEmpty().contains("embedded"))
        assertFalse(error?.message.orEmpty().contains("secret"))
    }

    private fun client(interceptor: Interceptor): WebDavBackupClient {
        return WebDavBackupClient(
            httpClient = OkHttpClient.Builder().addInterceptor(interceptor).build(),
            appPreferences = appPreferences,
        )
    }
}

private class WebDavResponseInterceptor(
    private val code: Int,
) : Interceptor {
    lateinit var request: okhttp3.Request
        private set

    override fun intercept(chain: Interceptor.Chain): Response {
        request = chain.request()
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("WebDAV test response")
            .body("".toResponseBody("application/xml".toMediaType()))
            .build()
    }
}
