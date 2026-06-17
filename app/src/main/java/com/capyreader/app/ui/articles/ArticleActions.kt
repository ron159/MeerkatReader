package com.capyreader.app.ui.articles

import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import com.jocmp.capy.Article

val LocalArticleActions = compositionLocalOf { ArticleActions() }

@Stable
data class ArticleActions(
    val markRead: (articleID: String) -> Unit = {},
    val star: (articleID: String) -> Unit = {},
    val markUnread: (articleID: String) -> Unit = {},
    val unstar: (articleID: String) -> Unit = {},
    val saveExternally: (articleID: String, onComplete: (Result<Unit>) -> Unit) -> Unit = { _, _ -> },
    val saveForLater: (url: String, onComplete: (Result<Unit>) -> Unit) -> Unit = { _, _ -> },
    val muteFeed: (Article) -> Boolean = { false },
    val muteSimilar: (Article) -> Boolean = { false },
    val notifyAuthor: (Article) -> Boolean = { false },
    val downloadOffline: (Article) -> Unit = {},
    val removeOffline: (Article) -> Unit = {},
    val showSaveForLater: Boolean = false,
)
