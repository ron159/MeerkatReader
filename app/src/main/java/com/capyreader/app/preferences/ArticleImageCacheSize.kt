package com.capyreader.app.preferences

import com.capyreader.app.R

enum class ArticleImageCacheSize(
    val translationKey: Int,
    val maxBytes: Long?,
) {
    STANDARD(
        translationKey = R.string.article_image_cache_size_standard,
        maxBytes = 250L * 1024L * 1024L,
    ),
    LARGE(
        translationKey = R.string.article_image_cache_size_large,
        maxBytes = 1024L * 1024L * 1024L,
    ),
    HUGE(
        translationKey = R.string.article_image_cache_size_huge,
        maxBytes = 5L * 1024L * 1024L * 1024L,
    ),
    UNLIMITED(
        translationKey = R.string.article_image_cache_size_unlimited,
        maxBytes = null,
    );

    companion object {
        val default = LARGE
    }
}
