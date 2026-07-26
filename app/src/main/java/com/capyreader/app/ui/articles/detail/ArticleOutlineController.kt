package com.capyreader.app.ui.articles.detail

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.capyreader.app.ui.components.WebViewState

@Stable
class ArticleOutlineController {
    var items by mutableStateOf<List<ArticleOutlineItem>>(emptyList())
        private set

    private var webViewState: WebViewState? = null

    internal fun bind(state: WebViewState) {
        webViewState = state
    }

    internal fun unbind(state: WebViewState) {
        if (webViewState === state) {
            webViewState = null
        }
    }

    internal fun update(items: List<ArticleOutlineItem>) {
        this.items = items
    }

    fun select(item: ArticleOutlineItem) {
        webViewState?.jumpToArticleHeading(item.targetID)
    }
}
