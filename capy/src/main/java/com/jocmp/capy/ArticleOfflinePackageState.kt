package com.jocmp.capy

enum class ArticleOfflinePackageState {
    NOT_DOWNLOADED,
    QUEUED,
    DOWNLOADING,
    READY,
    FAILED,
    STALE;

    companion object {
        fun from(value: String): ArticleOfflinePackageState {
            return entries.firstOrNull { it.name == value } ?: NOT_DOWNLOADED
        }
    }
}
