package com.jocmp.capy.articles

import com.jocmp.capy.common.optionalURL
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URL

data class ArticleImageRef(
    val originalSrc: String,
    val resolvedUrl: String,
    val ordinal: Int,
    val altText: String?,
)

class ArticleImageExtractor {
    fun extract(
        html: String?,
        articleUrl: URL?,
        siteUrl: String?,
        maxImages: Int = DEFAULT_MAX_IMAGES,
    ): List<ArticleImageRef> {
        if (html.isNullOrBlank() || maxImages <= 0) {
            return emptyList()
        }

        val baseUrl = articleUrl ?: optionalURL(siteUrl)
        val seen = mutableSetOf<String>()

        return Jsoup.parse(html)
            .select("img")
            .asSequence()
            .mapIndexedNotNull { index, image ->
                val originalSrc = image.imageSource() ?: return@mapIndexedNotNull null
                val resolvedUrl = resolveImageUrl(originalSrc, baseUrl) ?: return@mapIndexedNotNull null

                ImageCandidate(
                    originalSrc = originalSrc,
                    resolvedUrl = resolvedUrl,
                    ordinal = index,
                    altText = image.attr("alt").ifBlank { null },
                )
            }
            .mapNotNull { candidate ->
                if (!seen.add(candidate.resolvedUrl)) {
                    return@mapNotNull null
                }

                ArticleImageRef(
                    originalSrc = candidate.originalSrc,
                    resolvedUrl = candidate.resolvedUrl,
                    ordinal = candidate.ordinal,
                    altText = candidate.altText,
                )
            }
            .take(maxImages)
            .toList()
    }

    private fun Element.imageSource(): String? {
        return SOURCE_ATTRIBUTES
            .firstNotNullOfOrNull { attr ->
                attr(attr).trim().ifBlank { null }
            }
            ?: SRCSET_ATTRIBUTES.firstNotNullOfOrNull { attr ->
                attr(attr).bestSrcsetCandidate()
            }
    }

    private fun String.bestSrcsetCandidate(): String? =
        split(",")
            .mapNotNull { entry ->
                entry.trim()
                    .substringBefore(" ")
                    .trim()
                    .ifBlank { null }
            }
            .lastOrNull()

    private fun resolveImageUrl(src: String, baseUrl: URL?): String? {
        if (src.isBlank() || isUnsupportedScheme(src)) {
            return null
        }

        return try {
            val uri = URI(src)
            val resolved = if (uri.isAbsolute) {
                uri
            } else {
                baseUrl?.toURI()?.resolve(uri) ?: return null
            }

            if (resolved.scheme != "http" && resolved.scheme != "https") {
                return null
            }

            resolved.toURL().toString()
        } catch (_: Throwable) {
            null
        }
    }

    private fun isUnsupportedScheme(src: String): Boolean {
        val lower = src.lowercase()

        return lower.startsWith("data:") ||
            lower.startsWith("blob:") ||
            lower.startsWith("file:") ||
            lower.startsWith("content:") ||
            lower.startsWith("https://appassets.androidplatform.net/")
    }

    companion object {
        const val DEFAULT_MAX_IMAGES = Int.MAX_VALUE

        private val SOURCE_ATTRIBUTES = listOf(
            "data-src",
            "data-original",
            "data-lazy-src",
            "data-url",
            "src",
        )

        private val SRCSET_ATTRIBUTES = listOf(
            "srcset",
            "data-srcset",
        )
    }
}

private data class ImageCandidate(
    val originalSrc: String,
    val resolvedUrl: String,
    val ordinal: Int,
    val altText: String?,
)
