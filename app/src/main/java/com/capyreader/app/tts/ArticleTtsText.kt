package com.capyreader.app.tts

import com.jocmp.capy.Article
import org.jsoup.Jsoup
import java.text.BreakIterator
import java.util.Locale

object ArticleTtsText {
    private const val MAX_UTTERANCE_LENGTH = 3_000

    fun sentences(
        article: Article,
        locale: Locale = Locale.getDefault(),
    ): List<String> {
        val body = extractText(article.defaultContent)
        val text = listOf(article.title.trim(), body)
            .filter(String::isNotBlank)
            .joinToString(separator = ". ")

        return sentences(text, locale)
    }

    fun extractText(html: String): String {
        if (html.isBlank()) {
            return ""
        }

        return Jsoup.parse(html)
            .apply {
                select("script, style, noscript, template").remove()
            }
            .text()
            .replace(WHITESPACE, " ")
            .trim()
    }

    fun sentences(
        text: String,
        locale: Locale = Locale.getDefault(),
    ): List<String> {
        val normalized = text.replace(WHITESPACE, " ").trim()
        if (normalized.isBlank()) {
            return emptyList()
        }

        val iterator = BreakIterator.getSentenceInstance(locale)
        iterator.setText(normalized)
        val sentences = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()

        while (end != BreakIterator.DONE) {
            val sentence = normalized.substring(start, end).trim()
            if (sentence.isNotBlank()) {
                sentences += splitLongSentence(sentence)
            }
            start = end
            end = iterator.next()
        }

        if (sentences.isEmpty()) {
            sentences += splitLongSentence(normalized)
        }

        return sentences
    }

    private fun splitLongSentence(sentence: String): List<String> {
        if (sentence.length <= MAX_UTTERANCE_LENGTH) {
            return listOf(sentence)
        }

        val chunks = mutableListOf<String>()
        var remaining = sentence
        while (remaining.length > MAX_UTTERANCE_LENGTH) {
            val whitespace = remaining.lastIndexOf(' ', startIndex = MAX_UTTERANCE_LENGTH)
            val splitAt = whitespace.takeIf { it >= MAX_UTTERANCE_LENGTH / 2 }
                ?: MAX_UTTERANCE_LENGTH
            chunks += remaining.substring(0, splitAt).trim()
            remaining = remaining.substring(splitAt).trimStart()
        }
        if (remaining.isNotBlank()) {
            chunks += remaining
        }
        return chunks
    }

    private val WHITESPACE = Regex("\\s+")
}
