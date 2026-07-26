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
            val parts = tokenize(input.orEmpty())

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
                val value = parseQualifierValue(part.substring(separator + 1))
                if (value == null) {
                    text += part
                    return@forEach
                }

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

        private fun tokenize(input: String): List<String> {
            val parts = mutableListOf<String>()
            val current = StringBuilder()
            var insideQuotes = false
            var escaped = false

            fun flush() {
                if (current.isNotEmpty()) {
                    parts += current.toString()
                    current.clear()
                }
            }

            input.trim().forEach { character ->
                if (character.isWhitespace() && !insideQuotes) {
                    flush()
                    return@forEach
                }

                current.append(character)
                if (escaped) {
                    escaped = false
                } else {
                    when {
                        insideQuotes && character == '\\' -> escaped = true
                        character == '"' -> insideQuotes = !insideQuotes
                    }
                }
            }
            flush()

            return parts
        }

        private fun parseQualifierValue(rawValue: String): String? {
            val value = rawValue.trim()
            if (value.isEmpty()) return null
            if (!value.startsWith('"')) {
                return value.takeIf { '"' !in it }
            }
            if (value.length < 2 || !value.endsWith('"')) return null

            val unescaped = StringBuilder()
            var escaped = false
            value.substring(1, value.lastIndex).forEach { character ->
                if (escaped) {
                    when (character) {
                        '"', '\\' -> unescaped.append(character)
                        else -> {
                            unescaped.append('\\')
                            unescaped.append(character)
                        }
                    }
                    escaped = false
                } else {
                    when (character) {
                        '\\' -> escaped = true
                        '"' -> return null
                        else -> unescaped.append(character)
                    }
                }
            }
            if (escaped) return null

            return unescaped.toString().takeIf { it.isNotBlank() }
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
