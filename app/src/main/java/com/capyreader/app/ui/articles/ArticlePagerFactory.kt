package com.capyreader.app.ui.articles

import androidx.paging.PagingSource
import app.cash.sqldelight.paging3.QueryPagingSource
import com.jocmp.capy.ArticleSearchQuery
import com.jocmp.capy.Article
import com.jocmp.capy.ArticleFilter
import com.jocmp.capy.FeedPriority
import com.jocmp.capy.articles.SortOrder
import com.jocmp.capy.db.Database
import com.jocmp.capy.persistence.ArticleRecords
import kotlinx.coroutines.Dispatchers
import java.time.OffsetDateTime

class ArticlePagerFactory(private val database: Database) {
    private val articles = ArticleRecords(database)

    fun findArticles(
        filter: ArticleFilter,
        query: String?,
        sortOrder: SortOrder,
        since: OffsetDateTime
    ): PagingSource<Int, Article> {
        val searchQuery = ArticleSearchQuery.parse(query)

        return when (filter) {
            is ArticleFilter.Articles -> articleSource(filter, searchQuery, sortOrder, since)
            is ArticleFilter.Feeds -> feedSource(filter, searchQuery, sortOrder, since)
            is ArticleFilter.Folders -> folderSource(filter, searchQuery, sortOrder, since)
            is ArticleFilter.SavedSearches -> savedSearchSource(filter, searchQuery, sortOrder, since)
            is ArticleFilter.Today -> todaySource(filter, searchQuery, sortOrder, since)
        }
    }

    fun findArticleList(
        filter: ArticleFilter,
        query: String?,
        sortOrder: SortOrder,
        since: OffsetDateTime,
        limit: Long,
    ): List<Article> {
        val searchQuery = ArticleSearchQuery.parse(query)

        return when (filter) {
            is ArticleFilter.Articles -> articles.byStatus.all(
                status = filter.status,
                searchQuery = searchQuery,
                since = since,
                limit = limit,
                sortOrder = sortOrder,
                offset = 0,
            )

            is ArticleFilter.Feeds -> articles.byFeed.all(
                feedIDs = listOf(filter.feedID),
                status = filter.status,
                searchQuery = searchQuery,
                since = since,
                limit = limit,
                sortOrder = sortOrder,
                offset = 0,
                priority = FeedPriority.FEED,
            )

            is ArticleFilter.Folders -> articles.byFeed.all(
                feedIDs = database.taggingsQueries.findFeedIDs(folderTitle = filter.folderTitle).executeAsList(),
                status = filter.status,
                searchQuery = searchQuery,
                since = since,
                limit = limit,
                sortOrder = sortOrder,
                offset = 0,
                priority = FeedPriority.CATEGORY,
            )

            is ArticleFilter.SavedSearches -> articles.bySavedSearch.all(
                savedSearchID = filter.savedSearchID,
                status = filter.status,
                searchQuery = searchQuery,
                since = since,
                limit = limit,
                sortOrder = sortOrder,
                offset = 0,
            )

            is ArticleFilter.Today -> articles.byToday.all(
                status = filter.status,
                searchQuery = searchQuery,
                limit = limit,
                sortOrder = sortOrder,
                offset = 0,
                since = since,
            )
        }.executeAsList()
    }

    private fun articleSource(
        filter: ArticleFilter.Articles,
        searchQuery: ArticleSearchQuery,
        sortOrder: SortOrder,
        since: OffsetDateTime
    ): PagingSource<Int, Article> {
        return QueryPagingSource(
            countQuery = articles.byStatus.count(
                status = filter.status,
                searchQuery = searchQuery,
                since = since
            ),
            transacter = database.articlesQueries,
            context = Dispatchers.IO,
            queryProvider = { limit, offset ->
                articles.byStatus.all(
                    status = filter.status,
                    searchQuery = searchQuery,
                    since = since,
                    limit = limit,
                    sortOrder = sortOrder,
                    offset = offset,
                )
            }
        )
    }

    private fun feedSource(
        filter: ArticleFilter.Feeds,
        searchQuery: ArticleSearchQuery,
        sortOrder: SortOrder,
        since: OffsetDateTime,
    ): PagingSource<Int, Article> {
        val feedIDs = listOf(filter.feedID)

        return feedsSource(
            feedIDs = feedIDs,
            filter = filter,
            searchQuery = searchQuery,
            sortOrder = sortOrder,
            since = since,
            priority = FeedPriority.FEED,
        )
    }

    private fun folderSource(
        filter: ArticleFilter.Folders,
        searchQuery: ArticleSearchQuery,
        sortOrder: SortOrder,
        since: OffsetDateTime
    ): PagingSource<Int, Article> {
        val feedIDs = database
            .taggingsQueries
            .findFeedIDs(folderTitle = filter.folderTitle)
            .executeAsList()

        return feedsSource(
            feedIDs = feedIDs,
            filter = filter,
            searchQuery = searchQuery,
            sortOrder = sortOrder,
            since = since,
            priority = FeedPriority.CATEGORY,
        )
    }

    private fun feedsSource(
        feedIDs: List<String>,
        searchQuery: ArticleSearchQuery,
        filter: ArticleFilter,
        sortOrder: SortOrder,
        priority: FeedPriority,
        since: OffsetDateTime
    ): PagingSource<Int, Article> {
        return QueryPagingSource(
            countQuery = articles.byFeed.count(
                feedIDs = feedIDs,
                status = filter.status,
                searchQuery = searchQuery,
                since = since,
                priority = priority,
            ),
            transacter = database.articlesQueries,
            context = Dispatchers.IO,
            queryProvider = { limit, offset ->
                articles.byFeed.all(
                    feedIDs = feedIDs,
                    status = filter.status,
                    searchQuery = searchQuery,
                    since = since,
                    limit = limit,
                    sortOrder = sortOrder,
                    offset = offset,
                    priority = priority,
                )
            }
        )
    }

    private fun savedSearchSource(
        filter: ArticleFilter.SavedSearches,
        searchQuery: ArticleSearchQuery,
        sortOrder: SortOrder,
        since: OffsetDateTime
    ): PagingSource<Int, Article> {
        return QueryPagingSource(
            countQuery = articles.bySavedSearch.count(
                savedSearchID = filter.savedSearchID,
                status = filter.status,
                searchQuery = searchQuery,
                since = since
            ),
            transacter = database.articlesQueries,
            context = Dispatchers.IO,
            queryProvider = { limit, offset ->
                articles.bySavedSearch.all(
                    savedSearchID = filter.savedSearchID,
                    status = filter.status,
                    searchQuery = searchQuery,
                    since = since,
                    limit = limit,
                    sortOrder = sortOrder,
                    offset = offset,
                )
            }
        )
    }

    private fun todaySource(
        filter: ArticleFilter.Today,
        searchQuery: ArticleSearchQuery,
        sortOrder: SortOrder,
        since: OffsetDateTime
    ): PagingSource<Int, Article> {
        return QueryPagingSource(
            countQuery = articles.byToday.count(
                status = filter.status,
                searchQuery = searchQuery,
                since = since
            ),
            transacter = database.articlesQueries,
            context = Dispatchers.IO,
            queryProvider = { limit, offset ->
                articles.byToday.all(
                    status = filter.status,
                    searchQuery = searchQuery,
                    limit = limit,
                    sortOrder = sortOrder,
                    offset = offset,
                    since = since,
                )
            }
        )
    }
}
