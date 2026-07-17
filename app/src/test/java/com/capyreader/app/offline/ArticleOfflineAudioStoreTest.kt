package com.capyreader.app.offline

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArticleOfflineAudioStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `write exposes a local file URL`() {
        val store = ArticleOfflineAudioStore(temporaryFolder.root)
        val content = "audio bytes"

        val bytes = store.write(
            articleID = "article-1",
            sourceURL = "https://example.com/episode.mp3",
            mimeType = "audio/mpeg",
            body = content.toResponseBody("audio/mpeg".toMediaType()),
        )

        val localURL = requireNotNull(
            store.localURL(
                articleID = "article-1",
                sourceURL = "https://example.com/episode.mp3",
                mimeType = "audio/mpeg",
            )
        )

        assertEquals(content.toByteArray().size.toLong(), bytes)
        assertNotNull(localURL)
        assertTrue(localURL.startsWith("file:"))
        assertEquals(content, java.net.URI(localURL).toURL().readText())
    }

    @Test
    fun `retain removes stale files and reports owned bytes`() {
        val store = ArticleOfflineAudioStore(temporaryFolder.root)
        write(store, "article-1", "https://example.com/keep.mp3", "keep")
        write(store, "article-1", "https://example.com/remove.mp3", "remove")

        val bytes = store.retain(
            articleID = "article-1",
            enclosures = listOf(
                OfflineAudioEnclosure(
                    sourceURL = "https://example.com/keep.mp3",
                    mimeType = "audio/mpeg",
                )
            ),
        )

        assertEquals(4L, bytes)
        assertNotNull(
            store.localURL("article-1", "https://example.com/keep.mp3", "audio/mpeg")
        )
        assertNull(
            store.localURL("article-1", "https://example.com/remove.mp3", "audio/mpeg")
        )
    }

    @Test
    fun `delete unreferenced articles preserves current package files`() {
        val store = ArticleOfflineAudioStore(temporaryFolder.root)
        write(store, "article-1", "https://example.com/one.mp3", "one")
        write(store, "article-2", "https://example.com/two.mp3", "two")

        store.deleteUnreferencedArticles(listOf("article-2"))

        assertNull(
            store.localURL("article-1", "https://example.com/one.mp3", "audio/mpeg")
        )
        assertNotNull(
            store.localURL("article-2", "https://example.com/two.mp3", "audio/mpeg")
        )
    }

    private fun write(
        store: ArticleOfflineAudioStore,
        articleID: String,
        sourceURL: String,
        content: String,
    ) {
        store.write(
            articleID = articleID,
            sourceURL = sourceURL,
            mimeType = "audio/mpeg",
            body = content.toResponseBody("audio/mpeg".toMediaType()),
        )
    }
}
