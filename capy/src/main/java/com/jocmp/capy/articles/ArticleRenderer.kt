package com.jocmp.capy.articles

import com.jocmp.capy.Article
import com.jocmp.capy.Enclosure
import com.jocmp.capy.MacroProcessor
import com.jocmp.capy.preferences.Preference

class ArticleRenderer(
    private val template: String,
    private val textSize: Preference<Int>,
    private val fontOption: Preference<FontOption>,
    private val titleFontSize: Preference<Int>,
    private val textAlignment: Preference<TextAlignment>,
    private val titleFollowsBodyFont: Preference<Boolean>,
    private val enableHorizontalPagination: Preference<Boolean>,
    private val audioPlayerLabels: AudioPlayerLabels = AudioPlayerLabels(),
    private val imageRewriter: ArticleImageRewriter = ArticleImageRewriter(),
    private val audioEnclosureURL: (Article, Enclosure) -> String = { _, enclosure ->
        enclosure.url.toString()
    },
) {

    fun render(
        article: Article,
        byline: String,
        colors: Map<String, String>,
        hideImages: Boolean,
        feedName: String = article.feedName,
        articleTopMargin: String = "0px",
    ): String {
        val fontFamily = fontOption.get()
        val showPlaceholderTitle = article.title.isBlank()
        val enableHorizontalPagination = enableHorizontalPagination.get()

        val title = if (showPlaceholderTitle) {
            feedName
        } else {
            article.title
        }

        val displayFeedName = if (showPlaceholderTitle) {
            ""
        } else {
            feedName
        }

        val content = buildContent(article, hideImages)

        val titleFontFamily = if (titleFollowsBodyFont.get()) {
            fontFamily
        } else {
            FontOption.SYSTEM_DEFAULT
        }

        val substitutions = colors + mapOf(
            "external_link" to article.externalLink(),
            "title" to title,
            "byline" to byline,
            "feed_name" to displayFeedName,
            "font_size" to "${textSize.get()}px",
            "font_family" to fontFamily.slug,
            "font_preload" to fontPreload(fontFamily),
            "image_preload" to imagePreload(article, hideImages),
            "pre_white_space" to preWhiteSpace(enableHorizontalPagination),
            "code_overflow_x" to codeOverflowX(enableHorizontalPagination),
            "code_overflow_wrap" to codeOverflowWrap(enableHorizontalPagination),
            "table_overflow_x" to tableOverflowX(enableHorizontalPagination),
            "table_layout" to tableLayout(enableHorizontalPagination),
            "table_width" to tableWidth(enableHorizontalPagination),
            "title_font_size" to "${titleFontSize.get()}px",
            "title_text_align" to textAlignment.get().toCSS,
            "title_font_family" to titleFontFamily.slug,
            "article_top_margin" to articleTopMargin,
            "body" to content,
        )

        return MacroProcessor(template, substitutions).renderedText
    }

    private fun buildContent(article: Article, hideImages: Boolean): String {
        val audioEnclosures = article.audioEnclosureHTML(
            playLabel = audioPlayerLabels.play,
            pauseLabel = audioPlayerLabels.pause,
            enclosureURL = { enclosure -> audioEnclosureURL(article, enclosure) },
        )

        return if (article.parseFullContent) {
            audioEnclosures + parseHtml(article, hideImages)
        } else {
            val otherEnclosures = article.enclosureHTML()
            val articleContent = imageRewriter.rewrite(
                html = article.content,
                cachedImages = if (hideImages) emptyList() else article.cachedImages,
            )
            val content = audioEnclosures + articleContent + otherEnclosures

            content + postProcessScript(article, hideImages)
        }
    }

    private fun tableOverflowX(horizontalPagination: Boolean): String {
        return if (horizontalPagination) {
            "visible"
        } else {
            "auto"
        }
    }

    private fun preWhiteSpace(horizontalPagination: Boolean): String {
        return if (horizontalPagination) {
            "pre-wrap"
        } else {
            "pre"
        }
    }

    private fun codeOverflowX(horizontalPagination: Boolean): String {
        return if (horizontalPagination) {
            "visible"
        } else {
            "auto"
        }
    }

    private fun codeOverflowWrap(horizontalPagination: Boolean): String {
        return if (horizontalPagination) {
            "anywhere"
        } else {
            "normal"
        }
    }

    private fun tableLayout(horizontalPagination: Boolean): String {
        return if (horizontalPagination) {
            "fixed"
        } else {
            "auto"
        }
    }

    private fun tableWidth(horizontalPagination: Boolean): String {
        return if (horizontalPagination) {
            "100%"
        } else {
            "max-content"
        }
    }

    private fun fontPreload(fontFamily: FontOption): String {
        return when (fontFamily) {
            FontOption.SYSTEM_DEFAULT -> ""
            else -> """
                <link rel="preload" href="https://appassets.androidplatform.net/res/font/${fontFamily.slug}.ttf" as="font" type="font/ttf" crossorigin>
                """
        }
    }

    private fun imagePreload(article: Article, hideImages: Boolean): String {
        if (hideImages) {
            return ""
        }

        return article.cachedImages
            .take(IMAGE_PRELOAD_COUNT)
            .joinToString(separator = "\n") { image ->
                """<link rel="preload" href="${image.localURL}" as="image">"""
            }
    }

    companion object {
        private const val IMAGE_PRELOAD_COUNT = 8
    }
}

private fun Article.externalLink(): String {
    val potentialURL = url ?: siteURL

    return potentialURL?.toString() ?: ""
}

data class AudioPlayerLabels(
    val play: String = "Play",
    val pause: String = "Pause",
)
