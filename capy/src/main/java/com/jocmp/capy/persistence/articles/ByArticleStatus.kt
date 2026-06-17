package com.jocmp.capy.persistence.articles

import app.cash.sqldelight.Query
import com.jocmp.capy.Article
import com.jocmp.capy.ArticleSearchQuery
import com.jocmp.capy.ArticleStatus
import com.jocmp.capy.MarkRead
import com.jocmp.capy.articles.SortOrder
import com.jocmp.capy.db.Database
import com.jocmp.capy.persistence.hasAudioParam
import com.jocmp.capy.persistence.hasImageParam
import com.jocmp.capy.persistence.saved
import com.jocmp.capy.persistence.statusParams
import com.jocmp.capy.persistence.listMapper
import java.time.OffsetDateTime

class ByArticleStatus(private val database: Database) {
    fun all(
        status: ArticleStatus,
        searchQuery: ArticleSearchQuery = ArticleSearchQuery(),
        limit: Long,
        offset: Long,
        sortOrder: SortOrder,
        since: OffsetDateTime? = null
    ): Query<Article> {
        val (read, starred) = searchQuery.statusParams(status)
        val queries = database.articlesByStatusQueries

        return if (isNewestFirst(sortOrder)) {
            queries.allNewestFirst(
                read = read,
                starred = starred,
                limit = limit,
                offset = offset,
                lastReadAt = mapLastRead(read, since),
                lastUnstarredAt = mapLastUnstarred(starred, since),
                publishedSince = null,
                afterEpochSeconds = searchQuery.afterEpochSeconds,
                beforeEpochSeconds = searchQuery.beforeEpochSeconds,
                query = searchQuery.textOrNull,
                feed = searchQuery.feed,
                author = searchQuery.author,
                title = searchQuery.title,
                hasImage = searchQuery.hasImageParam,
                hasAudio = searchQuery.hasAudioParam,
                saved = searchQuery.saved,
                mapper = ::listMapper
            )
        } else {
            queries.allOldestFirst(
                read = read,
                starred = starred,
                limit = limit,
                offset = offset,
                lastReadAt = mapLastRead(read, since),
                lastUnstarredAt = mapLastUnstarred(starred, since),
                publishedSince = null,
                afterEpochSeconds = searchQuery.afterEpochSeconds,
                beforeEpochSeconds = searchQuery.beforeEpochSeconds,
                query = searchQuery.textOrNull,
                feed = searchQuery.feed,
                author = searchQuery.author,
                title = searchQuery.title,
                hasImage = searchQuery.hasImageParam,
                hasAudio = searchQuery.hasAudioParam,
                saved = searchQuery.saved,
                mapper = ::listMapper
            )
        }
    }

    fun unreadArticleIDs(
        status: ArticleStatus,
        range: MarkRead,
        sortOrder: SortOrder,
        searchQuery: ArticleSearchQuery,
    ): Query<String> {
        val (_, starred) = searchQuery.statusParams(status)
        val (afterArticleID, beforeArticleID) = range.toPair

        return database.articlesByStatusQueries.findArticleIDs(
            starred = starred,
            afterArticleID = afterArticleID,
            beforeArticleID = beforeArticleID,
            publishedSince = null,
            afterEpochSeconds = searchQuery.afterEpochSeconds,
            beforeEpochSeconds = searchQuery.beforeEpochSeconds,
            newestFirst = isNewestFirst(sortOrder),
            query = searchQuery.textOrNull,
            feed = searchQuery.feed,
            author = searchQuery.author,
            title = searchQuery.title,
            hasImage = searchQuery.hasImageParam,
            hasAudio = searchQuery.hasAudioParam,
            saved = searchQuery.saved,
        )
    }

    fun maxArrivedAt(): Long? {
        return database.articlesQueries.lastUpdatedAt().executeAsOne().MAX
    }

    fun count(
        status: ArticleStatus,
        searchQuery: ArticleSearchQuery = ArticleSearchQuery(),
        since: OffsetDateTime? = null
    ): Query<Long> {
        val (read, starred) = searchQuery.statusParams(status)

        return database.articlesByStatusQueries.countAll(
            read = read,
            starred = starred,
            query = searchQuery.textOrNull,
            feed = searchQuery.feed,
            author = searchQuery.author,
            title = searchQuery.title,
            hasImage = searchQuery.hasImageParam,
            hasAudio = searchQuery.hasAudioParam,
            saved = searchQuery.saved,
            lastReadAt = mapLastRead(read, since),
            lastUnstarredAt = mapLastUnstarred(starred, since),
            publishedSince = null,
            afterEpochSeconds = searchQuery.afterEpochSeconds,
            beforeEpochSeconds = searchQuery.beforeEpochSeconds,
        )
    }
}
