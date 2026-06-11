package com.jocmp.capy.articles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.jsoup.Jsoup

class ArticleImageRewriterTest {
    private val rewriter = ArticleImageRewriter()

    @Test
    fun rewritesCachedImagesToLocalUrls() {
        val html = """
            <p>Before</p>
            <img src="https://example.com/first.jpg" alt="First">
            <img src="https://example.com/second.jpg">
        """.trimIndent()

        val rewritten = rewriter.rewrite(
            html = html,
            cachedImages = listOf(
                CachedArticleImage(
                    assetID = "abc123",
                    originalSrc = "https://example.com/first.jpg",
                    resolvedURL = "https://example.com/first.jpg",
                    ordinal = 0,
                    altText = "First",
                    relativePath = "ab/abc123.jpg",
                    mimeType = "image/jpeg",
                )
            )
        )

        val images = Jsoup.parseBodyFragment(rewritten).body().select("img")
        val first = images[0]
        val second = images[1]

        assertEquals(
            expected = "https://appassets.androidplatform.net/article-images/ab/abc123.jpg",
            actual = first.attr("src"),
        )
        assertEquals(expected = "abc123", actual = first.attr("data-capy-image-id"))
        assertEquals(
            expected = "https://example.com/first.jpg",
            actual = first.attr("data-capy-original-src"),
        )
        assertEquals(expected = "https://example.com/second.jpg", actual = second.attr("src"))
        assertFalse(second.hasAttr("data-capy-image-id"))
    }

    @Test
    fun skipsCachedImageWhenSourceDoesNotMatch() {
        val rewritten = rewriter.rewrite(
            html = """<img src="https://example.com/changed.jpg">""",
            cachedImages = listOf(
                CachedArticleImage(
                    assetID = "abc123",
                    originalSrc = "https://example.com/original.jpg",
                    resolvedURL = "https://example.com/original.jpg",
                    ordinal = 0,
                    altText = null,
                    relativePath = "ab/abc123.jpg",
                    mimeType = "image/jpeg",
                )
            )
        )

        val image = Jsoup.parseBodyFragment(rewritten).body().selectFirst("img")!!

        assertEquals(expected = "https://example.com/changed.jpg", actual = image.attr("src"))
        assertFalse(image.hasAttr("data-capy-image-id"))
    }

    @Test
    fun rewritesLazyImageAttributes() {
        val rewritten = rewriter.rewrite(
            html = """<img src="/placeholder.gif" data-src="https://example.com/lazy.jpg">""",
            cachedImages = listOf(
                CachedArticleImage(
                    assetID = "abc123",
                    originalSrc = "https://example.com/lazy.jpg",
                    resolvedURL = "https://example.com/lazy.jpg",
                    ordinal = 0,
                    altText = null,
                    relativePath = "ab/abc123.jpg",
                    mimeType = "image/jpeg",
                )
            )
        )

        val image = Jsoup.parseBodyFragment(rewritten).body().selectFirst("img")!!

        assertEquals(
            expected = "https://appassets.androidplatform.net/article-images/ab/abc123.jpg",
            actual = image.attr("src"),
        )
        assertFalse(image.hasAttr("data-src"))
    }
}
