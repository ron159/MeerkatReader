package com.capyreader.app.ai

import android.content.Context
import com.capyreader.app.preferences.AiProvider
import com.capyreader.app.preferences.AppPreferences
import com.jocmp.capy.Article
import com.jocmp.capy.common.withIOContext
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
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class ArticleAiRepository(
    context: Context,
    private val appPreferences: AppPreferences,
    private val httpClient: OkHttpClient,
) {
    private val cacheDirectory = File(context.filesDir, "article-ai-cache")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun run(
        action: ArticleAiAction,
        article: Article,
        forceRefresh: Boolean,
    ): Result<String> = withIOContext {
        val settings = ArticleAiSettings.from(appPreferences)
        val content = article.plainTextContent()

        if (!settings.enabled) {
            return@withIOContext Result.failure(IllegalStateException("AI is disabled"))
        }

        if (settings.apiKey.isBlank()) {
            return@withIOContext Result.failure(IllegalStateException("API key is required"))
        }

        if (settings.model.isBlank()) {
            return@withIOContext Result.failure(IllegalStateException("Model is required"))
        }

        if (content.isBlank()) {
            return@withIOContext Result.failure(IllegalStateException("Article content is empty"))
        }

        val cacheFile = cacheFile(settings, action, article.id, content)

        if (!forceRefresh && cacheFile.exists()) {
            val cached = cacheFile.readText().trim()
            if (cached.isNotBlank()) {
                return@withIOContext Result.success(cached)
            }
        }

        requestAi(settings, action, article, content).onSuccess { result ->
            cacheDirectory.mkdirs()
            cacheFile.writeText(result)
        }
    }

    fun cachedResult(action: ArticleAiAction, article: Article): String? {
        val settings = ArticleAiSettings.from(appPreferences)
        val content = article.plainTextContent()
        val cacheFile = cacheFile(settings, action, article.id, content)
        return cacheFile.takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }
    }

    private suspend fun requestAi(
        settings: ArticleAiSettings,
        action: ArticleAiAction,
        article: Article,
        content: String,
    ): Result<String> {
        val request = Request.Builder()
            .url("${settings.baseUrl.trimEnd('/')}/chat/completions")
            .header("Authorization", "Bearer ${settings.apiKey}")
            .header("Content-Type", "application/json")
            .post(buildRequestBody(settings, action, article, content))
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
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

    private fun buildRequestBody(
        settings: ArticleAiSettings,
        action: ArticleAiAction,
        article: Article,
        content: String,
    ) = buildJsonObject {
        put("model", settings.model)
        put("stream", false)
        put("temperature", 0.2)
        put(
            "messages",
            buildJsonArray {
                addJsonObject {
                    put("role", "system")
                    put("content", "You help RSS reader users understand articles. Return concise Markdown without extra preamble.")
                }
                addJsonObject {
                    put("role", "user")
                    put("content", promptFor(settings, action, article, content))
                }
            }
        )
    }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

    private fun promptFor(
        settings: ArticleAiSettings,
        action: ArticleAiAction,
        article: Article,
        content: String,
    ): String {
        val template = settings.promptFor(action)
        val renderedTemplate = template.renderPromptVariables(settings.language, article, content)

        return if (template.contains("{content}")) {
            renderedTemplate
        } else if (action == ArticleAiAction.TRANSLATE) {
            """
                |$renderedTemplate
                |
                |Return only the translated article body. Do not include the title, URL, labels, explanations, or original text.
                |
                |$content
            """.trimMargin()
        } else {
            """
                |$renderedTemplate
                |
                |Title: ${article.title}
                |URL: ${article.url ?: ""}
                |
                |Article:
                |$content
            """.trimMargin()
        }
    }

    private fun String.renderPromptVariables(
        language: String,
        article: Article,
        content: String,
    ): String {
        return replace("{language}", language)
            .replace("{title}", article.title)
            .replace("{url}", article.url?.toString().orEmpty())
            .replace("{content}", content)
    }

    private fun cacheFile(
        settings: ArticleAiSettings,
        action: ArticleAiAction,
        articleID: String,
        content: String,
    ): File {
        val key = listOf(
            articleID,
            action.name,
            settings.provider.name,
            settings.baseUrl,
            settings.model,
            settings.language,
            settings.promptFor(action),
            sha256(content),
        ).joinToString("|")

        return File(cacheDirectory, "${sha256(key)}.txt")
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private data class ArticleAiSettings(
        val enabled: Boolean,
        val provider: AiProvider,
        val baseUrl: String,
        val apiKey: String,
        val model: String,
        val language: String,
        val translatePrompt: String,
        val summarizePrompt: String,
        val keyPointsPrompt: String,
    ) {
        fun promptFor(action: ArticleAiAction): String {
            return when (action) {
                ArticleAiAction.TRANSLATE -> translatePrompt
                ArticleAiAction.SUMMARIZE -> summarizePrompt
                ArticleAiAction.KEY_POINTS -> keyPointsPrompt
            }
        }

        companion object {
            fun from(appPreferences: AppPreferences): ArticleAiSettings {
                val provider = appPreferences.aiOptions.provider.get()

                return ArticleAiSettings(
                    enabled = appPreferences.aiOptions.enabled.get(),
                    provider = provider,
                    baseUrl = appPreferences.aiOptions.baseUrl.get().ifBlank { provider.defaultBaseUrl },
                    apiKey = appPreferences.aiOptions.apiKey.get().trim(),
                    model = appPreferences.aiOptions.model.get().ifBlank { provider.defaultModel },
                    language = appPreferences.aiOptions.language.get().ifBlank { "English" },
                    translatePrompt = appPreferences.aiOptions.translatePrompt.get()
                        .ifBlank { ArticleAiPrompts.TRANSLATE },
                    summarizePrompt = appPreferences.aiOptions.summarizePrompt.get()
                        .ifBlank { ArticleAiPrompts.SUMMARIZE },
                    keyPointsPrompt = appPreferences.aiOptions.keyPointsPrompt.get()
                        .ifBlank { ArticleAiPrompts.KEY_POINTS },
                )
            }
        }
    }
}
