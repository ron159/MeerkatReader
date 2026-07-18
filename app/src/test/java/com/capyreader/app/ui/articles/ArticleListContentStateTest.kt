package com.capyreader.app.ui.articles

import androidx.paging.LoadState
import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleListContentStateTest {
    @Test
    fun `shows articles while refresh is loading when cached items exist`() {
        assertEquals(
            ArticleListContentState.ARTICLES,
            articleListContentState(
                itemCount = 1,
                refreshInitialized = true,
                refreshLoadState = LoadState.Loading,
            ),
        )
    }

    @Test
    fun `shows loading before an empty refresh completes`() {
        assertEquals(
            ArticleListContentState.LOADING,
            articleListContentState(
                itemCount = 0,
                refreshInitialized = true,
                refreshLoadState = LoadState.Loading,
            ),
        )
    }

    @Test
    fun `shows error when an empty refresh fails`() {
        assertEquals(
            ArticleListContentState.ERROR,
            articleListContentState(
                itemCount = 0,
                refreshInitialized = true,
                refreshLoadState = LoadState.Error(IllegalStateException("load failed")),
            ),
        )
    }

    @Test
    fun `shows empty only after refresh completes`() {
        assertEquals(
            ArticleListContentState.EMPTY,
            articleListContentState(
                itemCount = 0,
                refreshInitialized = true,
                refreshLoadState = LoadState.NotLoading(endOfPaginationReached = true),
            ),
        )
    }

    @Test
    fun `keeps loading until initial account refresh completes`() {
        assertEquals(
            ArticleListContentState.LOADING,
            articleListContentState(
                itemCount = 0,
                refreshInitialized = false,
                refreshLoadState = LoadState.NotLoading(endOfPaginationReached = true),
            ),
        )
    }
}
