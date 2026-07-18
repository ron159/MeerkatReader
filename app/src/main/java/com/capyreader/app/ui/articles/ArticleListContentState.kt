package com.capyreader.app.ui.articles

import androidx.paging.LoadState

internal enum class ArticleListContentState {
    ARTICLES,
    LOADING,
    ERROR,
    EMPTY,
}

internal fun articleListContentState(
    itemCount: Int,
    refreshInitialized: Boolean,
    refreshLoadState: LoadState,
): ArticleListContentState {
    if (itemCount > 0) {
        return ArticleListContentState.ARTICLES
    }

    return when (refreshLoadState) {
        is LoadState.Loading -> ArticleListContentState.LOADING
        is LoadState.Error -> ArticleListContentState.ERROR
        is LoadState.NotLoading -> {
            if (refreshInitialized) {
                ArticleListContentState.EMPTY
            } else {
                ArticleListContentState.LOADING
            }
        }
    }
}
