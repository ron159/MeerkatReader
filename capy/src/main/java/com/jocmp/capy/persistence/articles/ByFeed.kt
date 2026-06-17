package com.jocmp.capy.persistence.articles

import app.cash.sqldelight.Query
import com.jocmp.capy.Article
import com.jocmp.capy.ArticleSearchQuery
import com.jocmp.capy.ArticleStatus
import com.jocmp.capy.FeedPriority
import com.jocmp.capy.MarkRead
import com.jocmp.capy.articles.SortOrder
import com.jocmp.capy.db.Database
import com.jocmp.capy.persistence.hasAudioParam
import com.jocmp.capy.persistence.hasImageParam
import com.jocmp.capy.persistence.listMapper
import com.jocmp.capy.persistence.saved
import com.jocmp.capy.persistence.statusParams
import java.time.OffsetDateTime

class ByFeed(private val database: Database) {
    fun all(
        feedIDs: List<String>,
        status: ArticleStatus,
        searchQuery: ArticleSearchQuery = ArticleSearchQuery(),
        since: OffsetDateTime,
        limit: Long,
        sortOrder: SortOrder,
        offset: Long,
        priority: FeedPriority,
    ): Query<Article> {
        val (read, starred) = searchQuery.statusParams(status)

        val queries = database.articlesByFeedQueries

        return if (isDescendingOrder(sortOrder)) {
            queries.allNewestFirst(
                feedIDs = feedIDs,
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
                priorities = priority.inclusivePriorities,
                mapper = ::listMapper
            )
        } else {
            queries.allOldestFirst(
                feedIDs = feedIDs,
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
                priorities = priority.inclusivePriorities,
                mapper = ::listMapper
            )
        }
    }

    fun unreadArticleIDs(
        status: ArticleStatus,
        feedIDs: List<String>,
        range: MarkRead,
        sortOrder: SortOrder,
        priority: FeedPriority,
        searchQuery: ArticleSearchQuery,
    ): Query<String> {
        val (_, starred) = searchQuery.statusParams(status)
        val (afterArticleID, beforeArticleID) = range.toPair

        return database.articlesByFeedQueries.findArticleIDs(
            feedIDs = feedIDs,
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
            priorities = priority.inclusivePriorities,
        )
    }

    fun count(
        feedIDs: List<String>,
        status: ArticleStatus,
        searchQuery: ArticleSearchQuery,
        since: OffsetDateTime?,
        priority: FeedPriority,
    ): Query<Long> {
        val (read, starred) = searchQuery.statusParams(status)

        return database.articlesByFeedQueries.countAll(
            feedIDs = feedIDs,
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
            priorities = priority.inclusivePriorities,
            publishedSince = null,
            afterEpochSeconds = searchQuery.afterEpochSeconds,
            beforeEpochSeconds = searchQuery.beforeEpochSeconds,
        )
    }
}
