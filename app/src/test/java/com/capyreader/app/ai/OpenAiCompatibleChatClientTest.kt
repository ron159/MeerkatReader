package com.capyreader.app.ai

import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class OpenAiCompatibleChatClientTest {
    @Test
    fun `returns completion content`() = runTest {
        val client = clientReturning(
            code = 200,
            body = """{"choices":[{"message":{"content":"  Summary text  "}}]}""",
        )

        assertEquals("Summary text", client.complete(request()).getOrThrow())
    }

    @Test
    fun `classifies provider HTTP failures without exposing response body`() = runTest {
        val cases = mapOf(
            400 to AiTransportErrorReason.PROVIDER_REJECTED,
            401 to AiTransportErrorReason.AUTHENTICATION,
            403 to AiTransportErrorReason.AUTHENTICATION,
            408 to AiTransportErrorReason.TIMEOUT,
            429 to AiTransportErrorReason.RATE_LIMIT,
            503 to AiTransportErrorReason.SERVER,
        )

        cases.forEach { (statusCode, expectedReason) ->
            val error = clientReturning(
                code = statusCode,
                body = "provider detail with secret-token",
            ).complete(request()).exceptionOrNull() as AiTransportException

            assertEquals(expectedReason, error.reason)
            assertEquals(statusCode, error.statusCode)
            assertFalse(error.message.orEmpty().contains("secret-token"))
        }
    }

    @Test
    fun `classifies malformed and empty successful responses`() = runTest {
        listOf("{", """{"choices":[]}""").forEach { responseBody ->
            val error = clientReturning(
                code = 200,
                body = responseBody,
            ).complete(request()).exceptionOrNull() as AiTransportException

            assertEquals(AiTransportErrorReason.INVALID_RESPONSE, error.reason)
        }
    }

    @Test
    fun `classifies timeout and connectivity failures`() = runTest {
        val timeout = clientThrowing(SocketTimeoutException("socket detail"))
            .complete(request())
            .exceptionOrNull() as AiTransportException
        val connectivity = clientThrowing(IOException("network detail"))
            .complete(request())
            .exceptionOrNull() as AiTransportException

        assertEquals(AiTransportErrorReason.TIMEOUT, timeout.reason)
        assertEquals(AiTransportErrorReason.CONNECTIVITY, connectivity.reason)
        assertFalse(timeout.message.orEmpty().contains("socket detail"))
        assertFalse(connectivity.message.orEmpty().contains("network detail"))
    }

    @Test
    fun `classifies invalid base URL`() = runTest {
        val error = OpenAiCompatibleChatClient(OkHttpClient())
            .complete(request().copy(baseUrl = "not a URL"))
            .exceptionOrNull() as AiTransportException

        assertEquals(AiTransportErrorReason.INVALID_CONFIGURATION, error.reason)
    }

    private fun clientReturning(
        code: Int,
        body: String = "",
    ): OpenAiCompatibleChatClient {
        return OpenAiCompatibleChatClient(
            OkHttpClient.Builder()
                .addInterceptor(StaticAiResponseInterceptor(code, body))
                .build()
        )
    }

    private fun clientThrowing(error: IOException): OpenAiCompatibleChatClient {
        return OpenAiCompatibleChatClient(
            OkHttpClient.Builder()
                .addInterceptor { throw error }
                .build()
        )
    }

    private fun request() = AiChatRequest(
        baseUrl = "https://provider.example/v1",
        apiKey = "secret-token",
        model = "example-model",
        messages = listOf(AiChatMessage(role = "user", content = "Hello")),
    )
}

private class StaticAiResponseInterceptor(
    private val code: Int,
    private val body: String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        return Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("Test response")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }
}
