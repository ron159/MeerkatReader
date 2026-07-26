package com.capyreader.app.tts

import com.jocmp.capy.Article
import com.jocmp.capy.persistence.ArticleTtsSource
import java.security.MessageDigest

data class ArticleTtsContent(
    val articleID: String,
    val articleTitle: String,
    val source: ArticleTtsSource,
    val text: String,
) {
    val contentKey: String = text.sha256()

    companion object {
        fun original(article: Article): ArticleTtsContent {
            val body = ArticleTtsText.extractText(article.defaultContent)
            val text = listOf(article.title.trim(), body)
                .filter(String::isNotBlank)
                .joinToString(separator = ". ")

            return ArticleTtsContent(
                articleID = article.id,
                articleTitle = article.title,
                source = ArticleTtsSource.ORIGINAL,
                text = text,
            )
        }

        fun generated(
            article: Article,
            source: ArticleTtsSource,
            text: String,
        ): ArticleTtsContent {
            require(source != ArticleTtsSource.ORIGINAL)

            return ArticleTtsContent(
                articleID = article.id,
                articleTitle = article.title,
                source = source,
                text = text.trim(),
            )
        }
    }
}

private fun String.sha256(): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(toByteArray())
        .joinToString(separator = "") { "%02x".format(it) }
}
