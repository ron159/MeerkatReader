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
import com.jocmp.capy.persistence.listMapper
import com.jocmp.capy.persistence.saved
import com.jocmp.capy.persistence.statusParams
import java.time.OffsetDateTime

class BySavedSearch(private val database: Database) {
    fun all(
        savedSearchID: String,
        status: ArticleStatus,
        searchQuery: ArticleSearchQuery = ArticleSearchQuery(),
        since: OffsetDateTime,
        limit: Long,
        sortOrder: SortOrder,
        offset: Long,
    ): Query<Article> {
        val (read, starred) = searchQuery.statusParams(status)

        val queries = database.articlesBySavedSearchQueries

        return if (isDescendingOrder(sortOrder)) {
            queries.allNewestFirst(
                savedSearchID = savedSearchID,
                query = searchQuery.textOrNull,
                feed = searchQuery.feed,
                author = searchQuery.author,
                title = searchQuery.title,
                hasImage = searchQuery.hasImageParam,
                hasAudio = searchQuery.hasAudioParam,
                saved = searchQuery.saved,
                read = read,
                starred = starred,
                limit = limit,
                offset = offset,
                lastReadAt = mapLastRead(read, since),
                lastUnstarredAt = mapLastUnstarred(starred, since),
                publishedSince = null,
                afterEpochSeconds = searchQuery.afterEpochSeconds,
                beforeEpochSeconds = searchQuery.beforeEpochSeconds,
                mapper = ::listMapper
            )
        } else {
            queries.allOldestFirst(
                savedSearchID = savedSearchID,
                query = searchQuery.textOrNull,
                feed = searchQuery.feed,
                author = searchQuery.author,
                title = searchQuery.title,
                hasImage = searchQuery.hasImageParam,
                hasAudio = searchQuery.hasAudioParam,
                saved = searchQuery.saved,
                read = read,
                starred = starred,
                limit = limit,
                offset = offset,
                lastReadAt = mapLastRead(read, since),
                lastUnstarredAt = mapLastUnstarred(starred, since),
                publishedSince = null,
                afterEpochSeconds = searchQuery.afterEpochSeconds,
                beforeEpochSeconds = searchQuery.beforeEpochSeconds,
                mapper = ::listMapper
            )
        }
    }

    fun unreadArticleIDs(
        status: ArticleStatus,
        savedSearchID: String,
        range: MarkRead,
        sortOrder: SortOrder,
        searchQuery: ArticleSearchQuery,
    ): Query<String> {
        val (_, starred) = searchQuery.statusParams(status)

        val (afterArticleID, beforeArticleID) = range.toPair

        return database.articlesBySavedSearchQueries.findArticleIDs(
            savedSearchID = savedSearchID,
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

    fun count(
        savedSearchID: String,
        status: ArticleStatus,
        searchQuery: ArticleSearchQuery,
        since: OffsetDateTime?
    ): Query<Long> {
        val (read, starred) = searchQuery.statusParams(status)

        return database.articlesBySavedSearchQueries.countAll(
            savedSearchID = savedSearchID,
            query = searchQuery.textOrNull,
            feed = searchQuery.feed,
            author = searchQuery.author,
            title = searchQuery.title,
            hasImage = searchQuery.hasImageParam,
            hasAudio = searchQuery.hasAudioParam,
            saved = searchQuery.saved,
            read = read,
            starred = starred,
            lastReadAt = mapLastRead(read, since),
            lastUnstarredAt = mapLastUnstarred(starred, since),
            publishedSince = null,
            afterEpochSeconds = searchQuery.afterEpochSeconds,
            beforeEpochSeconds = searchQuery.beforeEpochSeconds,
        )
    }
}
