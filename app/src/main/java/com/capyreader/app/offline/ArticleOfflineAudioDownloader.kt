package com.capyreader.app.offline

import com.jocmp.capy.Article
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class ArticleOfflineAudioDownloader(
    private val httpClient: OkHttpClient,
    private val store: ArticleOfflineAudioStore,
) {
    suspend fun download(article: Article): Long = withContext(Dispatchers.IO) {
        val enclosures = article.enclosures
            .filter { it.type.startsWith("audio/", ignoreCase = true) }
            .map { enclosure ->
                OfflineAudioEnclosure(
                    sourceURL = enclosure.url.toString(),
                    mimeType = enclosure.type,
                )
            }

        enclosures.forEach { enclosure ->
            currentCoroutineContext().ensureActive()

            val request = Request.Builder()
                .url(enclosure.sourceURL)
                .header("Accept", AUDIO_ACCEPT_HEADER)
                .apply {
                    article.url?.let { header("Referer", it.toString()) }
                }
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Audio download failed: HTTP ${response.code}")
                }

                store.write(
                    articleID = article.id,
                    sourceURL = enclosure.sourceURL,
                    mimeType = enclosure.mimeType,
                    body = response.body,
                )
            }
        }

        store.retain(article.id, enclosures)
    }

    companion object {
        private const val AUDIO_ACCEPT_HEADER = "audio/*,*/*;q=0.8"
    }
}
