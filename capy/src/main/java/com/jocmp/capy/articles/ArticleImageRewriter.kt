package com.jocmp.capy.articles

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class ArticleImageRewriter {
    fun rewrite(html: String, cachedImages: List<CachedArticleImage>): String {
        if (html.isBlank() || cachedImages.isEmpty()) {
            return html
        }

        val cachedBySource = cachedImages
            .flatMap { cached ->
                listOf(cached.originalSrc, cached.resolvedURL).map { source -> source to cached }
            }
            .toMap()
        val document = Jsoup.parseBodyFragment(html)

        document.body().select("img").forEach { image ->
            val cached = image.sourceCandidates()
                .firstNotNullOfOrNull { source -> cachedBySource[source] }
                ?: return@forEach

            image.attr("data-capy-image-id", cached.assetID)
            image.attr("data-capy-original-src", cached.resolvedURL)
            image.attr("src", cached.localURL)
            image.removeAttr("srcset")
            image.removeAttr("data-src")
            image.removeAttr("data-original")
            image.removeAttr("data-lazy-src")
            image.removeAttr("data-srcset")
        }

        return document.body().html()
    }

    private fun Element.sourceCandidates(): List<String> {
        val directSources = SOURCE_ATTRIBUTES
            .mapNotNull { attr ->
                attr(attr).trim().ifBlank { null }
            }

        val srcsetSources = SRCSET_ATTRIBUTES
            .flatMap { attr ->
                attr(attr)
                    .split(",")
                    .mapNotNull { entry ->
                        entry.trim()
                            .substringBefore(" ")
                            .trim()
                            .ifBlank { null }
                    }
            }

        return (directSources + srcsetSources).distinct()
    }

    companion object {
        private val SOURCE_ATTRIBUTES = listOf(
            "src",
            "data-src",
            "data-original",
            "data-lazy-src",
            "data-url",
        )

        private val SRCSET_ATTRIBUTES = listOf(
            "srcset",
            "data-srcset",
        )
    }
}
