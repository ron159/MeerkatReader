package com.capyreader.app.articleimages

import com.capyreader.app.preferences.AppPreferences
import com.jocmp.capy.logging.CapyLog
import com.jocmp.capy.persistence.ArticleImageDownloadCandidate
import com.jocmp.capy.persistence.ArticleImageRecords
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class ArticleImageDownloader(
    private val records: ArticleImageRecords,
    private val httpClient: OkHttpClient,
    private val store: ArticleImageStore,
    private val appPreferences: AppPreferences,
) {
    suspend fun downloadPending(limit: Long = DEFAULT_LIMIT) = withContext(Dispatchers.IO) {
        var remaining = limit

        while (remaining > 0) {
            val batchSize = minOf(remaining, DOWNLOAD_BATCH_SIZE)
            val candidates = records.downloadCandidates(batchSize)
            if (candidates.isEmpty()) {
                return@withContext
            }

            candidates.forEach { candidate ->
                currentCoroutineContext().ensureActive()
                download(candidate)
                remaining -= 1
            }
        }
    }

    suspend fun downloadPendingForArticle(articleID: String) = withContext(Dispatchers.IO) {
        while (true) {
            val candidates = records.downloadCandidatesForArticle(
                articleID = articleID,
                limit = DOWNLOAD_BATCH_SIZE,
            )
            if (candidates.isEmpty()) {
                return@withContext
            }

            candidates.forEach { candidate ->
                currentCoroutineContext().ensureActive()
                download(candidate)
            }
        }
    }

    private fun download(candidate: ArticleImageDownloadCandidate) {
        records.markDownloading(candidate.assetID)

        try {
            val request = Request.Builder()
                .url(candidate.sourceURL)
                .header("Accept", IMAGE_ACCEPT_HEADER)
                .apply {
                    candidate.articleURL?.let { header("Referer", it) }
                }
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    records.markFailed(candidate.assetID, "HTTP ${response.code}")
                    return
                }

                val mimeType = response.imageMimeType()
                if (mimeType == null) {
                    records.markSkipped(candidate.assetID, "Non-image response")
                    return
                }

                val stored = store.write(
                    assetID = candidate.assetID,
                    mimeType = mimeType,
                    sourceURL = candidate.sourceURL,
                    body = response.body,
                    maxBytes = maxImageBytes(),
                )

                records.markReady(
                    assetID = candidate.assetID,
                    finalURL = response.request.url.toString(),
                    relativePath = stored.relativePath,
                    mimeType = mimeType,
                    byteSize = stored.byteSize,
                    etag = response.header("ETag"),
                    lastModified = response.header("Last-Modified"),
                )
            }
        } catch (e: CancellationException) {
            records.markPending(candidate.assetID, "Download cancelled")
            throw e
        } catch (e: Exception) {
            records.markFailed(candidate.assetID, e.message)
            CapyLog.warn(
                "article_image_download",
                mapOf(
                    "asset_id" to candidate.assetID,
                    "error_type" to e::class.simpleName,
                    "error_message" to e.message,
                )
            )
        }
    }

    private fun Response.imageMimeType(): String? {
        val mimeType = body.contentType()?.let { "${it.type}/${it.subtype}" }
            ?: header("Content-Type")?.substringBefore(";")?.trim()

        return mimeType
            ?.lowercase()
            ?.takeIf { it.startsWith("image/") }
    }

    private fun maxImageBytes(): Long {
        return when (appPreferences.articleImageCacheSize.get().maxBytes) {
            null -> Long.MAX_VALUE
            else -> ArticleImageStore.MAX_IMAGE_BYTES
        }
    }

    companion object {
        private const val DEFAULT_LIMIT = Long.MAX_VALUE
        private const val DOWNLOAD_BATCH_SIZE = 500L
        private const val IMAGE_ACCEPT_HEADER =
            "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
    }
}
