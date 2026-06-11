package com.jocmp.capy.articles

import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals

class ArticleImageExtractorTest {
    private val extractor = ArticleImageExtractor()

    @Test
    fun extractsResolvedImageUrls() {
        val refs = extractor.extract(
            html = """
                <p>Before</p>
                <img src="/images/first.jpg" alt="First">
                <img src="second.png">
                <img src="//cdn.example.com/third.webp" alt="">
            """.trimIndent(),
            articleUrl = URI("https://example.com/articles/post.html").toURL(),
            siteUrl = null,
        )

        assertEquals(
            expected = listOf(
                "https://example.com/images/first.jpg",
                "https://example.com/articles/second.png",
                "https://cdn.example.com/third.webp",
            ),
            actual = refs.map { it.resolvedUrl },
        )
        assertEquals(expected = listOf(0, 1, 2), actual = refs.map { it.ordinal })
        assertEquals(expected = "First", actual = refs.first().altText)
        assertEquals(expected = null, actual = refs.last().altText)
    }

    @Test
    fun fallsBackToSiteUrl() {
        val refs = extractor.extract(
            html = """<img src="images/photo.jpg">""",
            articleUrl = null,
            siteUrl = "https://example.com/blog/",
        )

        assertEquals(
            expected = listOf("https://example.com/blog/images/photo.jpg"),
            actual = refs.map { it.resolvedUrl },
        )
    }

    @Test
    fun skipsUnsupportedSchemesAndDuplicates() {
        val refs = extractor.extract(
            html = """
                <img src="data:image/png;base64,abc">
                <img src="blob:https://example.com/image">
                <img src="file:///tmp/image.jpg">
                <img src="/images/first.jpg">
                <img src="https://example.com/images/first.jpg">
                <img src="https://other.example.com/image.jpg">
            """.trimIndent(),
            articleUrl = URI("https://example.com/articles/post.html").toURL(),
            siteUrl = null,
        )

        assertEquals(
            expected = listOf(
                "https://example.com/images/first.jpg",
                "https://other.example.com/image.jpg",
            ),
            actual = refs.map { it.resolvedUrl },
        )
        assertEquals(expected = listOf(3, 5), actual = refs.map { it.ordinal })
    }

    @Test
    fun limitsImageCount() {
        val refs = extractor.extract(
            html = """
                <img src="https://example.com/1.jpg">
                <img src="https://example.com/2.jpg">
                <img src="https://example.com/3.jpg">
            """.trimIndent(),
            articleUrl = null,
            siteUrl = null,
            maxImages = 2,
        )

        assertEquals(
            expected = listOf(
                "https://example.com/1.jpg",
                "https://example.com/2.jpg",
            ),
            actual = refs.map { it.resolvedUrl },
        )
    }

    @Test
    fun extractsLazyImageAttributesAndSrcset() {
        val refs = extractor.extract(
            html = """
                <img src="/placeholder.gif" data-src="/images/lazy.jpg">
                <img srcset="/small.jpg 320w, /large.jpg 1200w">
            """.trimIndent(),
            articleUrl = URI("https://example.com/articles/post.html").toURL(),
            siteUrl = null,
        )

        assertEquals(
            expected = listOf(
                "https://example.com/images/lazy.jpg",
                "https://example.com/large.jpg",
            ),
            actual = refs.map { it.resolvedUrl },
        )
    }
}
