package com.capyreader.app.ui.articles

import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.jocmp.capy.Account
import com.jocmp.capy.Article
import com.jocmp.capy.ArticleFilter
import com.jocmp.capy.articles.SortOrder
import com.jocmp.capy.common.withIOContext
import java.time.OffsetDateTime

fun Account.buildArticlePager(
    filter: ArticleFilter,
    query: String? = null,
    sortOrder: SortOrder = SortOrder.NEWEST_FIRST,
    since: OffsetDateTime = OffsetDateTime.now()
): Pager<Int, Article> {
    return Pager(
        config = PagingConfig(
            pageSize = 100,
            prefetchDistance = 10,
        ),
        pagingSourceFactory = {
            ArticlePagerFactory(database).findArticles(
                filter = filter,
                query = query,
                sortOrder = sortOrder,
                since = since
            )
        }
    )
}

suspend fun Account.findArticles(
    filter: ArticleFilter,
    query: String? = null,
    sortOrder: SortOrder = SortOrder.NEWEST_FIRST,
    since: OffsetDateTime = OffsetDateTime.now(),
    limit: Long = 30,
): List<Article> = withIOContext {
    ArticlePagerFactory(database).findArticleList(
        filter = filter,
        query = query,
        sortOrder = sortOrder,
        since = since,
        limit = limit,
    )
}
