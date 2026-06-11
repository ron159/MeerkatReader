package com.capyreader.app.preferences

import com.capyreader.app.R

enum class ArticleImageDownloadMode {
    OFF,
    WIFI_ONLY,
    ALWAYS;

    val translationKey: Int
        get() = when (this) {
            OFF -> R.string.article_image_download_mode_off
            WIFI_ONLY -> R.string.article_image_download_mode_wifi_only
            ALWAYS -> R.string.article_image_download_mode_always
        }

    companion object {
        val default = WIFI_ONLY
    }
}
