package com.capyreader.app.ai

import kotlinx.serialization.Serializable

@Serializable
enum class ArticleAiErrorReason {
    DISABLED,
    DISABLED_FOR_FEED,
    API_KEY_REQUIRED,
    MODEL_REQUIRED,
    CONTENT_EMPTY,
    QUESTION_REQUIRED,
    NO_DIGEST_ARTICLES,
    INVALID_CONFIGURATION,
    AUTHENTICATION,
    RATE_LIMIT,
    TIMEOUT,
    CONNECTIVITY,
    SERVER,
    PROVIDER_REJECTED,
    INVALID_RESPONSE,
    REQUEST_FAILED,
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
    val invalidConfiguration: String,
    val authentication: String,
    val rateLimit: String,
    val timeout: String,
    val connectivity: String,
    val server: String,
    val providerRejected: String,
    val invalidResponse: String,
) {
    fun messageFor(error: Throwable): String {
        if (error is ArticleAiException) {
            return messageFor(error.reason)
        }

        return requestFailed
    }

    fun messageFor(message: String?): String {
        val detail = message?.takeIf { it.isNotBlank() }

        ArticleAiErrorReason.entries.firstOrNull { it.name == detail }?.let {
            return messageFor(it)
        }

        return requestFailed
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
            ArticleAiErrorReason.INVALID_CONFIGURATION -> invalidConfiguration
            ArticleAiErrorReason.AUTHENTICATION -> authentication
            ArticleAiErrorReason.RATE_LIMIT -> rateLimit
            ArticleAiErrorReason.TIMEOUT -> timeout
            ArticleAiErrorReason.CONNECTIVITY -> connectivity
            ArticleAiErrorReason.SERVER -> server
            ArticleAiErrorReason.PROVIDER_REJECTED -> providerRejected
            ArticleAiErrorReason.INVALID_RESPONSE -> invalidResponse
            ArticleAiErrorReason.REQUEST_FAILED -> requestFailed
        }
    }
}

fun Throwable.toArticleAiException(): ArticleAiException {
    if (this is ArticleAiException) {
        return this
    }

    val reason = when ((this as? AiTransportException)?.reason) {
        AiTransportErrorReason.INVALID_CONFIGURATION -> ArticleAiErrorReason.INVALID_CONFIGURATION
        AiTransportErrorReason.AUTHENTICATION -> ArticleAiErrorReason.AUTHENTICATION
        AiTransportErrorReason.RATE_LIMIT -> ArticleAiErrorReason.RATE_LIMIT
        AiTransportErrorReason.TIMEOUT -> ArticleAiErrorReason.TIMEOUT
        AiTransportErrorReason.CONNECTIVITY -> ArticleAiErrorReason.CONNECTIVITY
        AiTransportErrorReason.SERVER -> ArticleAiErrorReason.SERVER
        AiTransportErrorReason.PROVIDER_REJECTED -> ArticleAiErrorReason.PROVIDER_REJECTED
        AiTransportErrorReason.INVALID_RESPONSE -> ArticleAiErrorReason.INVALID_RESPONSE
        null -> ArticleAiErrorReason.REQUEST_FAILED
    }

    return ArticleAiException(reason = reason)
}
