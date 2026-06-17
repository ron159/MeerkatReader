package com.jocmp.capy

import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArticleSearchQueryTest {
    @Test
    fun parse_preservesPlainText() {
        val query = ArticleSearchQuery.parse("android security update")

        assertEquals("android security update", query.text)
        assertNull(query.status)
    }

    @Test
    fun parse_extractsSupportedQualifiers() {
        val query = ArticleSearchQuery.parse(
            "android is:unread feed:Google author:Alice title:Security after:2026-01-01 before:2026-01-31 has:image has:audio"
        )

        assertEquals("android", query.text)
        assertEquals(ArticleSearchStatus.UNREAD, query.status)
        assertEquals("Google", query.feed)
        assertEquals("Alice", query.author)
        assertEquals("Security", query.title)
        assertEquals(true, query.hasImage)
        assertEquals(true, query.hasAudio)
        assertEquals(
            LocalDate.parse("2026-01-01").atStartOfDay().toEpochSecond(ZoneOffset.UTC),
            query.afterEpochSeconds,
        )
        assertEquals(
            LocalDate.parse("2026-01-31").atTime(LocalTime.MAX).toEpochSecond(ZoneOffset.UTC),
            query.beforeEpochSeconds,
        )
    }

    @Test
    fun parse_keepsUnknownOrInvalidQualifiersInPlainText() {
        val query = ArticleSearchQuery.parse("android site:example after:not-a-date has:video")

        assertEquals("android site:example after:not-a-date has:video", query.text)
        assertNull(query.afterEpochSeconds)
        assertNull(query.hasAudio)
        assertNull(query.hasImage)
    }
}
