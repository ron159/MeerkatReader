package com.jocmp.capy.persistence

import com.jocmp.capy.ArticleSearchQuery
import com.jocmp.capy.ArticleSearchStatus
import com.jocmp.capy.ArticleStatus

internal data class ArticleSearchStatusParams(
    val read: Boolean?,
    val starred: Boolean?,
)

internal fun ArticleSearchQuery.statusParams(defaultStatus: ArticleStatus): ArticleSearchStatusParams {
    return when (status) {
        ArticleSearchStatus.READ -> ArticleSearchStatusParams(read = true, starred = null)
        ArticleSearchStatus.UNREAD -> ArticleSearchStatusParams(read = false, starred = null)
        ArticleSearchStatus.STARRED -> ArticleSearchStatusParams(read = null, starred = true)
        ArticleSearchStatus.SAVED,
        null -> defaultStatus.toStatusPair.toSearchParams()
    }
}

internal val ArticleSearchQuery.saved: Boolean?
    get() = when (status) {
        ArticleSearchStatus.SAVED -> true
        else -> null
    }

internal val ArticleSearchQuery.hasImageParam: Long?
    get() = hasImage?.toSqlFlag()

internal val ArticleSearchQuery.hasAudioParam: Long?
    get() = hasAudio?.toSqlFlag()

private fun ArticleStatusPair.toSearchParams() = ArticleSearchStatusParams(
    read = read,
    starred = starred,
)

private fun Boolean.toSqlFlag(): Long = if (this) 1L else 0L
