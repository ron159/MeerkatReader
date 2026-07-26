package com.capyreader.app.integrations.wallabag

import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.preferences.InMemorySecretStore
import com.jocmp.capy.Article
import com.jocmp.capy.preferences.Preference
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.URL
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class WallabagClientTest {
    private val appPreferences = mockk<AppPreferences>()
    private val options = mockk<AppPreferences.WallabagOptions>()
    private val serverUrl = mockk<Preference<String>>()
    private val accessToken = mockk<Preference<String>>()

    init {
        every { appPreferences.wallabagOptions } returns options
        every { options.serverUrl } returns serverUrl
        every { options.accessToken } returns accessToken
        every { serverUrl.get() } returns "https://wallabag.example"
        every { accessToken.get() } returns "secret-token"
    }

    @Test
    fun `save sends bearer token and article form`() = runTest {
        val interceptor = StaticResponseInterceptor(
            code = 200,
            body = """{"id":42,"url":"https://example.com/article"}""",
        )
        val client = WallabagClient(
            httpClient = OkHttpClient.Builder().addInterceptor(interceptor).build(),
            appPreferences = appPreferences,
        )

        val result = client.save(article())

        assertEquals("42", result.getOrThrow().remoteID)
        val request = interceptor.request
        assertEquals(
            "https://wallabag.example/api/entries.json",
            request.url.toString(),
        )
        assertEquals("Bearer secret-token", request.header("Authorization"))
        val buffer = Buffer()
        requireNotNull(request.body).writeTo(buffer)
        val form = buffer.readUtf8()
        assertTrue(form.contains("url=https%3A%2F%2Fexample.com%2Farticle"))
        assertTrue(form.contains("title=Example+article"))
    }

    @Test
    fun `save reads token from encrypted preference facade`() = runTest {
        val preferences = AppPreferences(
            RuntimeEnvironment.getApplication(),
            InMemorySecretStore(),
        ).also {
            it.wallabagOptions.serverUrl.set("https://wallabag.example")
            it.wallabagOptions.accessToken.set("encrypted-token")
        }
        val interceptor = StaticResponseInterceptor(
            code = 200,
            body = """{"id":42,"url":"https://example.com/article"}""",
        )
        val client = WallabagClient(
            httpClient = OkHttpClient.Builder().addInterceptor(interceptor).build(),
            appPreferences = preferences,
        )

        client.save(article()).getOrThrow()

        assertEquals(
            "Bearer encrypted-token",
            interceptor.request.header("Authorization"),
        )
    }

    @Test
    fun `authentication failure is permanent`() = runTest {
        val client = clientReturning(code = 401)

        val error = client.save(article()).exceptionOrNull()

        assertTrue(error is WallabagAuthenticationException)
        assertFalse((error as WallabagExportException).retryable)
    }

    @Test
    fun `server failure is retryable`() = runTest {
        val client = clientReturning(code = 503)

        val error = client.save(article()).exceptionOrNull()

        assertTrue(error is WallabagHttpException)
        assertTrue((error as WallabagExportException).retryable)
    }

    private fun clientReturning(code: Int): WallabagClient {
        return WallabagClient(
            httpClient = OkHttpClient.Builder()
                .addInterceptor(StaticResponseInterceptor(code = code))
                .build(),
            appPreferences = appPreferences,
        )
    }

    private fun article() = Article(
        id = "article-1",
        feedID = "feed-1",
        title = "Example article",
        author = null,
        contentHTML = "",
        url = URL("https://example.com/article"),
        summary = "",
        imageURL = null,
        updatedAt = ZonedDateTime.now(),
        publishedAt = ZonedDateTime.now(),
        read = false,
        starred = false,
    )
}

private class StaticResponseInterceptor(
    private val code: Int,
    private val body: String = "",
) : Interceptor {
    lateinit var request: okhttp3.Request
        private set

    override fun intercept(chain: Interceptor.Chain): Response {
        request = chain.request()
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("Test response")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }
}
