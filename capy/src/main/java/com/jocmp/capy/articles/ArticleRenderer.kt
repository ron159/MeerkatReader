package com.jocmp.capy.articles

import com.jocmp.capy.Article
import com.jocmp.capy.MacroProcessor
import com.jocmp.capy.preferences.Preference

class ArticleRenderer(
    private val template: String,
    private val textSize: Preference<Int>,
    private val fontOption: Preference<FontOption>,
    private val titleFontSize: Preference<Int>,
    private val textAlignment: Preference<TextAlignment>,
    private val titleFollowsBodyFont: Preference<Boolean>,
    private val enableHorizontalScroll: Preference<Boolean>,
    private val audioPlayerLabels: AudioPlayerLabels = AudioPlayerLabels(),
    private val imageRewriter: ArticleImageRewriter = ArticleImageRewriter(),
) {

    fun render(
        article: Article,
        byline: String,
        colors: Map<String, String>,
        hideImages: Boolean,
        feedName: String = article.feedName,
    ): String {
        val fontFamily = fontOption.get()
        val showPlaceholderTitle = article.title.isBlank()
        val enableHorizontalScroll = enableHorizontalScroll.get()

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
            "pre_white_space" to preWhiteSpace(enableHorizontalScroll),
            "table_overflow_x" to tableOverflowX(enableHorizontalScroll),
            "title_font_size" to "${titleFontSize.get()}px",
            "title_text_align" to textAlignment.get().toCSS,
            "title_font_family" to titleFontFamily.slug,
            "body" to content,
        )

        return MacroProcessor(template, substitutions).renderedText
    }

    private fun buildContent(article: Article, hideImages: Boolean): String {
        return if (article.parseFullContent) {
            parseHtml(article, hideImages)
        } else {
            val audioEnclosures = article.audioEnclosureHTML(
                playLabel = audioPlayerLabels.play,
                pauseLabel = audioPlayerLabels.pause,
            )
            val otherEnclosures = article.enclosureHTML()
            val articleContent = imageRewriter.rewrite(
                html = article.content,
                cachedImages = if (hideImages) emptyList() else article.cachedImages,
            )
            val content = audioEnclosures + articleContent + otherEnclosures

            content + postProcessScript(article, hideImages)
        }
    }

    private fun tableOverflowX(horizontalScroll: Boolean): String {
        return if (horizontalScroll) {
            "visible"
        } else {
            "auto"
        }
    }

    private fun preWhiteSpace(horizontalScroll: Boolean): String {
        return if (horizontalScroll) {
            "pre-wrap"
        } else {
            "pre"
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
