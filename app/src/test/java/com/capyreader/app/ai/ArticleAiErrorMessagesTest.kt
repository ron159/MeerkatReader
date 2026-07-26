package com.capyreader.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ArticleAiErrorMessagesTest {
    private val messages = ArticleAiErrorMessages(
        requestFailed = "request-failed",
        disabled = "disabled",
        disabledForFeed = "disabled-for-feed",
        apiKeyRequired = "api-key-required",
        modelRequired = "model-required",
        contentEmpty = "content-empty",
        questionRequired = "question-required",
        noDigestArticles = "no-digest-articles",
        invalidConfiguration = "invalid-configuration",
        authentication = "authentication",
        rateLimit = "rate-limit",
        timeout = "timeout",
        connectivity = "connectivity",
        server = "server",
        providerRejected = "provider-rejected",
        invalidResponse = "invalid-response",
    )

    @Test
    fun `maps typed transport reasons to recovery messages`() {
        val expected = mapOf(
            ArticleAiErrorReason.INVALID_CONFIGURATION to "invalid-configuration",
            ArticleAiErrorReason.AUTHENTICATION to "authentication",
            ArticleAiErrorReason.RATE_LIMIT to "rate-limit",
            ArticleAiErrorReason.TIMEOUT to "timeout",
            ArticleAiErrorReason.CONNECTIVITY to "connectivity",
            ArticleAiErrorReason.SERVER to "server",
            ArticleAiErrorReason.PROVIDER_REJECTED to "provider-rejected",
            ArticleAiErrorReason.INVALID_RESPONSE to "invalid-response",
            ArticleAiErrorReason.REQUEST_FAILED to "request-failed",
        )

        expected.forEach { (reason, message) ->
            assertEquals(message, messages.messageFor(ArticleAiException(reason)))
            assertEquals(message, messages.messageFor(reason.name))
        }
    }

    @Test
    fun `unknown exception details are not shown`() {
        val detail = "provider response containing secret-token"

        val fromException = messages.messageFor(IllegalStateException(detail))
        val fromStoredMessage = messages.messageFor(detail)

        assertEquals("request-failed", fromException)
        assertEquals("request-failed", fromStoredMessage)
        assertFalse(fromException.contains("secret-token"))
        assertFalse(fromStoredMessage.contains("secret-token"))
    }
}
