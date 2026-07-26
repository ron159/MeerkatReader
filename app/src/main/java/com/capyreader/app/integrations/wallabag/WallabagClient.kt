package com.capyreader.app.integrations.wallabag

import com.capyreader.app.preferences.AppPreferences
import com.jocmp.capy.Article
import com.jocmp.capy.ArticleExportResult
import com.jocmp.capy.common.withIOContext
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class WallabagClient(
    private val httpClient: OkHttpClient,
    private val appPreferences: AppPreferences,
) {
    suspend fun save(article: Article): Result<ArticleExportResult> = withIOContext {
        try {
            Result.success(saveArticle(article))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    private fun saveArticle(article: Article): ArticleExportResult {
        val options = appPreferences.wallabagOptions
        val serverUrl = options.serverUrl.get().trim().trimEnd('/')
        val accessToken = options.accessToken.get().trim()
        val articleUrl = article.url?.toString()
            ?: throw WallabagConfigurationException("Article does not have a URL")

        if (serverUrl.isBlank() || accessToken.isBlank()) {
            throw WallabagConfigurationException("Wallabag is not configured")
        }

        val endpoint = "$serverUrl/api/entries.json".toHttpUrlOrNull()
            ?: throw WallabagConfigurationException("Wallabag server URL is invalid")
        val form = FormBody.Builder()
            .add("url", articleUrl)
            .add("title", article.title)
            .build()
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $accessToken")
            .post(form)
            .build()

        httpClient.newCall(request).execute().use { response ->
            when {
                response.code == 401 || response.code == 403 -> {
                    throw WallabagAuthenticationException()
                }

                !response.isSuccessful -> {
                    throw WallabagHttpException(response.code)
                }
            }

            val body = response.body.string()
            val remoteID = runCatching {
                json.parseToJsonElement(body)
                    .jsonObject["id"]
                    ?.jsonPrimitive
                    ?.content
            }.getOrNull()

            return ArticleExportResult(remoteID = remoteID)
        }
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
        }
    }
}

open class WallabagExportException(
    message: String,
    val retryable: Boolean,
) : IOException(message)

class WallabagConfigurationException(message: String) :
    WallabagExportException(message, retryable = false)

class WallabagAuthenticationException :
    WallabagExportException("Wallabag authentication failed", retryable = false)

class WallabagHttpException(
    val statusCode: Int,
) : WallabagExportException(
    message = "Wallabag request failed (HTTP $statusCode)",
    retryable = statusCode == 408 || statusCode == 429 || statusCode >= 500,
)

internal fun wallabagExportErrorMessage(error: Throwable): String {
    return when (error) {
        is WallabagAuthenticationException -> "Wallabag authentication failed"
        is WallabagHttpException -> "Wallabag request failed (HTTP ${error.statusCode})"
        is WallabagConfigurationException -> "Wallabag is not configured"
        is IOException -> "Wallabag connection failed"
        else -> "Wallabag export failed"
    }
}
