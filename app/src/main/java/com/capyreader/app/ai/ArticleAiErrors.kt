package com.capyreader.app.ai

enum class ArticleAiErrorReason {
    DISABLED,
    DISABLED_FOR_FEED,
    API_KEY_REQUIRED,
    MODEL_REQUIRED,
    CONTENT_EMPTY,
    QUESTION_REQUIRED,
    NO_DIGEST_ARTICLES,
}

class ArticleAiException(
    val reason: ArticleAiErrorReason,
) : IllegalStateException(reason.name)

data class ArticleAiErrorMessages(
    val requestFailed: String,
    val disabled: String,
    val disabledForFeed: String,
    val apiKeyRequired: String,
    val modelRequired: String,
    val contentEmpty: String,
    val questionRequired: String,
    val noDigestArticles: String,
) {
    fun messageFor(error: Throwable): String {
        if (error is ArticleAiException) {
            return messageFor(error.reason)
        }

        return messageFor(error.localizedMessage ?: error.message)
    }

    fun messageFor(message: String?): String {
        val detail = message?.takeIf { it.isNotBlank() }

        ArticleAiErrorReason.values().firstOrNull { it.name == detail }?.let {
            return messageFor(it)
        }

        return detail ?: requestFailed
    }

    private fun messageFor(reason: ArticleAiErrorReason): String {
        return when (reason) {
            ArticleAiErrorReason.DISABLED -> disabled
            ArticleAiErrorReason.DISABLED_FOR_FEED -> disabledForFeed
            ArticleAiErrorReason.API_KEY_REQUIRED -> apiKeyRequired
            ArticleAiErrorReason.MODEL_REQUIRED -> modelRequired
            ArticleAiErrorReason.CONTENT_EMPTY -> contentEmpty
            ArticleAiErrorReason.QUESTION_REQUIRED -> questionRequired
            ArticleAiErrorReason.NO_DIGEST_ARTICLES -> noDigestArticles
        }
    }
}
