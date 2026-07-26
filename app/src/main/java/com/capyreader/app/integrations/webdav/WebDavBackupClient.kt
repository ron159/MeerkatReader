package com.capyreader.app.integrations.webdav

import com.capyreader.app.preferences.AppPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class WebDavBackupClient(
    private val httpClient: OkHttpClient,
    private val appPreferences: AppPreferences,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun testConnection(): Result<Unit> = execute {
        val configuration = configuration()
        val request = authenticatedRequest(configuration)
            .url(configuration.directoryUrl)
            .header("Depth", "0")
            .method("PROPFIND", EMPTY_REQUEST_BODY)
            .build()

        executeRequest(request)
    }

    suspend fun upload(
        fileName: String,
        payload: ByteArray,
    ): Result<Unit> = execute {
        val configuration = configuration()
        val targetUrl = backupUrl(configuration.directoryUrl, fileName)
        val request = authenticatedRequest(configuration)
            .url(targetUrl)
            .put(payload.toRequestBody(BACKUP_MEDIA_TYPE))
            .build()

        executeRequest(request)
    }

    private suspend fun execute(block: () -> Unit): Result<Unit> = withContext(ioDispatcher) {
        try {
            block()
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: WebDavBackupException) {
            Result.failure(error)
        } catch (error: Exception) {
            Result.failure(WebDavNetworkException(error))
        }
    }

    private fun authenticatedRequest(configuration: WebDavConfiguration): Request.Builder {
        return Request.Builder()
            .header(
                "Authorization",
                Credentials.basic(
                    configuration.username,
                    configuration.password,
                    Charsets.UTF_8,
                ),
            )
    }

    private fun executeRequest(request: Request) {
        httpClient.newCall(request).execute().use { response ->
            when {
                response.code == 401 || response.code == 403 -> {
                    throw WebDavAuthenticationException()
                }

                !response.isSuccessful -> {
                    throw WebDavHttpException(response.code)
                }
            }
        }
    }

    private fun configuration(): WebDavConfiguration {
        val options = appPreferences.webDavBackupOptions
        val directoryUrl = options.directoryUrl.get().trim().toHttpUrlOrNull()
            ?: throw WebDavConfigurationException()
        val username = options.username.get().trim()
        val password = options.password.get()

        if (directoryUrl.username.isNotEmpty() ||
            directoryUrl.password.isNotEmpty() ||
            username.isBlank() ||
            password.isBlank()
        ) {
            throw WebDavConfigurationException()
        }

        return WebDavConfiguration(
            directoryUrl = directoryUrl,
            username = username,
            password = password,
        )
    }

    private fun backupUrl(directoryUrl: HttpUrl, fileName: String): HttpUrl {
        if (fileName.isBlank()) {
            throw WebDavConfigurationException()
        }

        return directoryUrl.newBuilder()
            .addPathSegment(fileName)
            .build()
    }

    private data class WebDavConfiguration(
        val directoryUrl: HttpUrl,
        val username: String,
        val password: String,
    )

    private companion object {
        val BACKUP_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val EMPTY_REQUEST_BODY = ByteArray(0).toRequestBody()
    }
}

open class WebDavBackupException(
    message: String,
    val retryable: Boolean,
    cause: Throwable? = null,
) : IOException(message, cause)

class WebDavConfigurationException :
    WebDavBackupException("WebDAV backup is not configured", retryable = false)

class WebDavAuthenticationException :
    WebDavBackupException("WebDAV authentication failed", retryable = false)

class WebDavHttpException(
    val statusCode: Int,
) : WebDavBackupException(
    message = "WebDAV request failed (HTTP $statusCode)",
    retryable = statusCode == 408 || statusCode == 429 || statusCode >= 500,
)

class WebDavNetworkException(cause: Throwable) :
    WebDavBackupException("WebDAV connection failed", retryable = true, cause = cause)

internal fun webDavBackupErrorMessage(error: Throwable): String {
    return (error as? WebDavBackupException)?.message ?: "WebDAV backup failed"
}

internal fun stripWebDavUrlCredentials(value: String): String {
    val url = value.trim().toHttpUrlOrNull() ?: return value
    if (url.username.isEmpty() && url.password.isEmpty()) {
        return value
    }

    return url.newBuilder()
        .username("")
        .password("")
        .build()
        .toString()
}
