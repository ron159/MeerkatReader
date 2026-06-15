package com.capyreader.app.preferences

import androidx.annotation.StringRes
import com.capyreader.app.R
import com.jocmp.capy.ArticleStatus

enum class DefaultHomeTab(
    val articleStatus: ArticleStatus,
    @param:StringRes val translationKey: Int,
) {
    FEEDS(
        articleStatus = ArticleStatus.ALL,
        translationKey = R.string.feed_nav_drawer_title,
    ),
    UNREAD(
        articleStatus = ArticleStatus.UNREAD,
        translationKey = R.string.filter_unread,
    ),
    STARRED(
        articleStatus = ArticleStatus.STARRED,
        translationKey = R.string.filter_starred,
    );

    companion object {
        val default
            get() = FEEDS
    }
}
