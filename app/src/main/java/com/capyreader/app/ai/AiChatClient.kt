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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

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
        val httpRequest = Request.Builder()
            .url("${request.baseUrl.trimEnd('/')}/chat/completions")
            .header("Authorization", "Bearer ${request.apiKey}")
            .header("Content-Type", "application/json")
            .post(buildRequestBody(request))
            .build()

        return try {
            httpClient.newCall(httpRequest).execute().use { response ->
                val responseBody = response.body.string()

                if (!response.isSuccessful) {
                    return Result.failure(IOException("AI API request failed: HTTP ${response.code}"))
                }

                val result = json.parseToJsonElement(responseBody)
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

                if (result.isBlank()) {
                    Result.failure(IOException("AI API returned an empty result"))
                } else {
                    Result.success(result)
                }
            }
        } catch (e: Throwable) {
            Result.failure(e)
        }
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
