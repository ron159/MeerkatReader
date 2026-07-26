package com.jocmp.capy.accounts

import com.jocmp.capy.AccountPreferences
import com.jocmp.capy.ClientCertManager
import com.jocmp.capy.InMemoryDataStore
import com.jocmp.capy.accounts.feedbin.FeedbinOkHttpClient
import com.jocmp.capy.accounts.miniflux.MinifluxOkHttpClient
import com.jocmp.capy.accounts.reader.ReaderOkHttpClient
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AccountCredentialHttpClientTest {
    @JvmField
    @Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `Feedbin reads injected account password preference`() {
        val preferences = preferences(
            username = "reader@example.com",
            secret = "feedbin-secret",
        )
        val client = FeedbinOkHttpClient.forAccount(cachePath(), preferences)

        val request = authenticatedRequest(client)

        assertEquals(
            Credentials.basic("reader@example.com", "feedbin-secret"),
            request.header("Authorization"),
        )
    }

    @Test
    fun `Miniflux basic auth reads injected account password preference`() {
        val preferences = preferences(
            username = "reader",
            secret = "miniflux-password",
        )
        val client = MinifluxOkHttpClient.forAccount(
            path = cachePath(),
            preferences = preferences,
            source = Source.MINIFLUX,
            clientCertManager = clientCertManager,
        )

        val request = authenticatedRequest(client)

        assertEquals(
            Credentials.basic("reader", "miniflux-password"),
            request.header("Authorization"),
        )
    }

    @Test
    fun `Miniflux token auth reads injected account password preference`() {
        val preferences = preferences(
            username = "reader",
            secret = "miniflux-token",
        )
        val client = MinifluxOkHttpClient.forAccount(
            path = cachePath(),
            preferences = preferences,
            source = Source.MINIFLUX_TOKEN,
            clientCertManager = clientCertManager,
        )

        val request = authenticatedRequest(client)

        assertEquals("miniflux-token", request.header("X-Auth-Token"))
    }

    @Test
    fun `Reader API reads injected account password preference`() {
        val preferences = preferences(
            username = "reader",
            secret = "reader-session-token",
        )
        val client = ReaderOkHttpClient.forAccount(
            path = cachePath(),
            preferences = preferences,
            clientCertManager = clientCertManager,
        )

        val request = authenticatedRequest(client)

        assertEquals(
            "GoogleLogin auth=reader-session-token",
            request.header("Authorization"),
        )
    }

    private fun preferences(
        username: String,
        secret: String,
    ): AccountPreferences {
        val store = InMemoryDataStore()
        store.getString("username").set(username)
        val passwordPreference = store.getString("encrypted_password").also {
            it.set(secret)
        }

        return AccountPreferences(
            store = store,
            passwordPreference = passwordPreference,
        )
    }

    private fun authenticatedRequest(client: OkHttpClient): Request {
        lateinit var capturedRequest: Request
        val capturingClient = client.newBuilder()
            .addInterceptor { chain ->
                capturedRequest = chain.request()
                Response.Builder()
                    .request(capturedRequest)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody())
                    .build()
            }
            .build()

        capturingClient.newCall(
            Request.Builder()
                .url("https://example.com/")
                .build()
        ).execute().close()

        return capturedRequest
    }

    private fun cachePath() = temporaryFolder.newFolder().toURI()

    private companion object {
        val clientCertManager = ClientCertManager { builder, _ -> builder }
    }
}
