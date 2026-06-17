package com.jocmp.capy

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

data class ArticleSearchQuery(
    val text: String = "",
    val status: ArticleSearchStatus? = null,
    val feed: String? = null,
    val folder: String? = null,
    val author: String? = null,
    val title: String? = null,
    val afterEpochSeconds: Long? = null,
    val beforeEpochSeconds: Long? = null,
    val hasImage: Boolean? = null,
    val hasAudio: Boolean? = null,
) {
    val textOrNull: String?
        get() = text.trim().ifBlank { null }

    companion object {
        fun parse(input: String?): ArticleSearchQuery {
            val parts = input.orEmpty()
                .trim()
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }

            val text = mutableListOf<String>()
            var status: ArticleSearchStatus? = null
            var feed: String? = null
            var folder: String? = null
            var author: String? = null
            var title: String? = null
            var afterEpochSeconds: Long? = null
            var beforeEpochSeconds: Long? = null
            var hasImage: Boolean? = null
            var hasAudio: Boolean? = null

            parts.forEach { part ->
                val separator = part.indexOf(':')
                if (separator <= 0 || separator == part.lastIndex) {
                    text += part
                    return@forEach
                }

                val key = part.substring(0, separator).lowercase()
                val value = part.substring(separator + 1).trim()

                when (key) {
                    "is" -> {
                        ArticleSearchStatus.from(value)?.let {
                            status = it
                        } ?: run {
                            text += part
                        }
                    }

                    "feed" -> feed = value
                    "folder" -> folder = value
                    "author" -> author = value
                    "title" -> title = value
                    "after" -> {
                        parseStartDate(value)?.let {
                            afterEpochSeconds = it
                        } ?: run {
                            text += part
                        }
                    }

                    "before" -> {
                        parseEndDate(value)?.let {
                            beforeEpochSeconds = it
                        } ?: run {
                            text += part
                        }
                    }

                    "has" -> when (value.lowercase()) {
                        "image" -> hasImage = true
                        "audio" -> hasAudio = true
                        else -> text += part
                    }

                    else -> text += part
                }
            }

            return ArticleSearchQuery(
                text = text.joinToString(" "),
                status = status,
                feed = feed,
                folder = folder,
                author = author,
                title = title,
                afterEpochSeconds = afterEpochSeconds,
                beforeEpochSeconds = beforeEpochSeconds,
                hasImage = hasImage,
                hasAudio = hasAudio,
            )
        }

        private fun parseStartDate(value: String): Long? {
            return runCatching {
                LocalDate.parse(value)
                    .atStartOfDay()
                    .toEpochSecond(ZoneOffset.UTC)
            }.getOrNull()
        }

        private fun parseEndDate(value: String): Long? {
            return runCatching {
                LocalDate.parse(value)
                    .atTime(LocalTime.MAX)
                    .toEpochSecond(ZoneOffset.UTC)
            }.getOrNull()
        }
    }
}

enum class ArticleSearchStatus {
    READ,
    UNREAD,
    STARRED,
    SAVED;

    companion object {
        fun from(value: String): ArticleSearchStatus? {
            return when (value.lowercase()) {
                "read" -> READ
                "unread" -> UNREAD
                "starred" -> STARRED
                "saved" -> SAVED
                else -> null
            }
        }
    }
}
