package com.capyreader.app.preferences

import com.capyreader.app.R

enum class ArticleStatusListDisplay {
    ALL_ARTICLES,
    GROUPED_BY_FEED;

    val translationKey: Int
        get() = when (this) {
            ALL_ARTICLES -> R.string.article_status_list_display_all_articles
            GROUPED_BY_FEED -> R.string.article_status_list_display_grouped_by_feed
        }

    companion object {
        val default
            get() = ALL_ARTICLES
    }
}
