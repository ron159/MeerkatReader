package com.capyreader.app.ui.articles.detail

import android.content.Context
import com.capyreader.app.R
import com.capyreader.app.ai.plainTextContent
import com.jocmp.capy.Article
import com.jocmp.capy.common.DisplayTimeFormats
import com.jocmp.capy.common.toDeviceDateTime
import kotlin.math.ceil
import kotlin.math.max

fun Article.byline(
    context: Context,
    formats: DisplayTimeFormats,
): String {
    val deviceDateTime = publishedAt.toDeviceDateTime()
    val date = formats.longDate.format(deviceDateTime)
    val time = formats.time.format(deviceDateTime)
    val readingTime = context.getString(R.string.article_reading_time_minutes, estimatedReadingMinutes())

    val publishedByline = if (!author.isNullOrBlank()) {
        context.getString(R.string.article_byline, date, time, author)
    } else {
        context.getString(R.string.article_byline_date_only, date, time)
    }

    return context.getString(R.string.article_byline_with_reading_time, publishedByline, readingTime)
}

private fun Article.estimatedReadingMinutes(): Int {
    val text = plainTextContent()
    if (text.isBlank()) return 1

    val wordCount = text.split(Regex("\\s+")).count { it.isNotBlank() }
    val units = if (wordCount > 1) {
        wordCount.toDouble()
    } else {
        text.length / CJK_CHARS_PER_WORD_EQUIVALENT
    }

    return max(1, ceil(units / WORDS_PER_MINUTE).toInt())
}

private const val WORDS_PER_MINUTE = 220.0
private const val CJK_CHARS_PER_WORD_EQUIVALENT = 500.0 / WORDS_PER_MINUTE
