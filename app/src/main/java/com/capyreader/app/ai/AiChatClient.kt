package com.capyreader.app.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException

interface AiChatClient {
    suspend fun complete(request: AiChatRequest): Result<String>
}

data class AiChatRequest(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val messages: List<AiChatMessage>,
    val temperature: Double = 0.2,
)

data class AiChatMessage(
    val role: String,
    val content: String,
)

class OpenAiCompatibleChatClient(
    private val httpClient: OkHttpClient,
) : AiChatClient {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun complete(request: AiChatRequest): Result<String> {
        val httpRequest = try {
            Request.Builder()
                .url("${request.baseUrl.trimEnd('/')}/chat/completions")
                .header("Authorization", "Bearer ${request.apiKey}")
                .header("Content-Type", "application/json")
                .post(buildRequestBody(request))
                .build()
        } catch (_: IllegalArgumentException) {
            return Result.failure(
                AiTransportException(
                    reason = AiTransportErrorReason.INVALID_CONFIGURATION,
                )
            )
        }

        return try {
            httpClient.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(httpFailure(response.code))
                }

                val responseBody = response.body.string()
                val result = parseResponse(responseBody)

                if (result.isBlank()) {
                    Result.failure(
                        AiTransportException(AiTransportErrorReason.INVALID_RESPONSE)
                    )
                } else {
                    Result.success(result)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: SocketTimeoutException) {
            Result.failure(
                AiTransportException(
                    reason = AiTransportErrorReason.TIMEOUT,
                )
            )
        } catch (_: SerializationException) {
            Result.failure(
                AiTransportException(
                    reason = AiTransportErrorReason.INVALID_RESPONSE,
                )
            )
        } catch (_: IOException) {
            Result.failure(
                AiTransportException(
                    reason = AiTransportErrorReason.CONNECTIVITY,
                )
            )
        } catch (_: RuntimeException) {
            Result.failure(
                AiTransportException(
                    reason = AiTransportErrorReason.INVALID_RESPONSE,
                )
            )
        }
    }

    private fun parseResponse(responseBody: String): String {
        return json.parseToJsonElement(responseBody)
            .jsonObject["choices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
            ?.get("content")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.trim()
            .orEmpty()
    }

    private fun httpFailure(statusCode: Int): AiTransportException {
        val reason = when (statusCode) {
            401, 403 -> AiTransportErrorReason.AUTHENTICATION
            408 -> AiTransportErrorReason.TIMEOUT
            429 -> AiTransportErrorReason.RATE_LIMIT
            in 500..599 -> AiTransportErrorReason.SERVER
            else -> AiTransportErrorReason.PROVIDER_REJECTED
        }

        return AiTransportException(
            reason = reason,
            statusCode = statusCode,
        )
    }

    private fun buildRequestBody(request: AiChatRequest) = buildJsonObject {
        put("model", request.model)
        put("stream", false)
        put("temperature", request.temperature)
        put(
            "messages",
            buildJsonArray {
                request.messages.forEach { message ->
                    addJsonObject {
                        put("role", message.role)
                        put("content", message.content)
                    }
                }
            }
        )
    }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
}

enum class AiTransportErrorReason {
    INVALID_CONFIGURATION,
    AUTHENTICATION,
    RATE_LIMIT,
    TIMEOUT,
    CONNECTIVITY,
    SERVER,
    PROVIDER_REJECTED,
    INVALID_RESPONSE,
}

class AiTransportException(
    val reason: AiTransportErrorReason,
    val statusCode: Int? = null,
) : IOException(reason.name)
