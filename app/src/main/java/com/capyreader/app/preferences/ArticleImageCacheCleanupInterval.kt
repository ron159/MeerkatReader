package com.capyreader.app.preferences

import com.capyreader.app.R

enum class ArticleImageCacheCleanupInterval(
    val translationKey: Int,
    val intervalSeconds: Long?,
) {
    MANUAL(
        translationKey = R.string.article_image_cache_cleanup_manual,
        intervalSeconds = null,
    ),
    WEEKLY(
        translationKey = R.string.article_image_cache_cleanup_weekly,
        intervalSeconds = 7L * 24L * 60L * 60L,
    ),
    MONTHLY(
        translationKey = R.string.article_image_cache_cleanup_monthly,
        intervalSeconds = 30L * 24L * 60L * 60L,
    ),
    ALWAYS(
        translationKey = R.string.article_image_cache_cleanup_always,
        intervalSeconds = 0L,
    );

    companion object {
        val default = WEEKLY
    }
}
