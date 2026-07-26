package com.capyreader.app.ai

import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.preferences.InMemorySecretStore
import com.jocmp.capy.Account
import com.jocmp.capy.Article
import com.jocmp.capy.persistence.ArticleAiDigestRecords
import com.jocmp.capy.persistence.ArticleAiResultInput
import com.jocmp.capy.persistence.ArticleAiResultRecord
import com.jocmp.capy.persistence.ArticleAiResultRecords
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.net.URL
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
class ArticleAiRepositoryTest {
    private lateinit var appPreferences: AppPreferences
    private lateinit var account: Account
    private lateinit var resultRecords: ArticleAiResultRecords
    private lateinit var digestRecords: ArticleAiDigestRecords

    @Before
    fun setUp() {
        appPreferences = AppPreferences(
            RuntimeEnvironment.getApplication(),
            InMemorySecretStore(),
        ).also {
            it.clearAll()
            it.aiOptions.enabled.set(true)
            it.aiOptions.baseUrl.set("https://provider.example/v1")
            it.aiOptions.apiKey.set("secret-token")
            it.aiOptions.model.set("example-model")
            it.aiOptions.language.set("Test Language")
        }
        account = mockk()
        resultRecords = mockk(relaxed = true)
        digestRecords = mockk(relaxed = true)
        coEvery { account.findFeed(any()) } returns null
        coEvery { resultRecords.find(any()) } returns null
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `maps transport failures to stable repository reasons`() = runTest {
        val cases = mapOf(
            AiTransportErrorReason.INVALID_CONFIGURATION to ArticleAiErrorReason.INVALID_CONFIGURATION,
            AiTransportErrorReason.AUTHENTICATION to ArticleAiErrorReason.AUTHENTICATION,
            AiTransportErrorReason.RATE_LIMIT to ArticleAiErrorReason.RATE_LIMIT,
            AiTransportErrorReason.TIMEOUT to ArticleAiErrorReason.TIMEOUT,
            AiTransportErrorReason.CONNECTIVITY to ArticleAiErrorReason.CONNECTIVITY,
            AiTransportErrorReason.SERVER to ArticleAiErrorReason.SERVER,
            AiTransportErrorReason.PROVIDER_REJECTED to ArticleAiErrorReason.PROVIDER_REJECTED,
            AiTransportErrorReason.INVALID_RESPONSE to ArticleAiErrorReason.INVALID_RESPONSE,
        )

        cases.forEach { (transportReason, expectedReason) ->
            val client = RecordingAiChatClient(
                Result.failure(AiTransportException(transportReason))
            )
            val error = repository(client)
                .run(
                    action = ArticleAiAction.KEY_POINTS,
                    article = article("article-${transportReason.name}"),
                    forceRefresh = true,
                )
                .exceptionOrNull() as ArticleAiException

            assertEquals(expectedReason, error.reason)
            assertFalse(error.message.orEmpty().contains("secret-token"))
        }
    }

    @Test
    fun `unknown client failures become generic request failure`() = runTest {
        val client = RecordingAiChatClient(
            Result.failure(IllegalStateException("provider body secret"))
        )

        val error = repository(client)
            .run(
                action = ArticleAiAction.KEY_POINTS,
                article = article("article-unknown"),
                forceRefresh = true,
            )
            .exceptionOrNull() as ArticleAiException

        assertEquals(ArticleAiErrorReason.REQUEST_FAILED, error.reason)
        assertFalse(error.message.orEmpty().contains("provider body secret"))
    }

    @Test
    fun `renders prompt variables before sending request`() = runTest {
        appPreferences.aiOptions.keyPointsPrompt.set(
            "Language={language}; title={title}; body={content}"
        )
        val client = RecordingAiChatClient(
            Result.failure(
                AiTransportException(AiTransportErrorReason.PROVIDER_REJECTED)
            )
        )

        repository(client).run(
            action = ArticleAiAction.KEY_POINTS,
            article = article("article-prompt"),
            forceRefresh = true,
        )

        val prompt = client.requests.single().messages.last().content
        assertEquals("secret-token", client.requests.single().apiKey)
        assertTrue(prompt.contains("Language=Test Language"))
        assertTrue(prompt.contains("title=Example article"))
        assertTrue(prompt.contains("body=First paragraph."))
        assertFalse(prompt.contains("{content}"))
    }

    @Test
    fun `SQL cached result skips provider request`() = runTest {
        coEvery { resultRecords.find(any()) } answers {
            firstArg<ArticleAiResultInput>().toRecord("Cached result")
        }
        val client = RecordingAiChatClient(Result.success("Network result"))

        val result = repository(client).run(
            action = ArticleAiAction.KEY_POINTS,
            article = article("article-cached"),
            forceRefresh = false,
        )

        assertEquals("Cached result", result.getOrThrow())
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun `question changes produce different cache keys`() = runTest {
        val client = RecordingAiChatClient(
            Result.failure(
                AiTransportException(AiTransportErrorReason.PROVIDER_REJECTED)
            )
        )
        val firstInput = slot<ArticleAiResultInput>()
        coEvery { resultRecords.find(capture(firstInput)) } returns null
        repository(client).run(
            action = ArticleAiAction.QUESTION,
            article = article("article-cache-key"),
            forceRefresh = false,
            question = "First question?",
        )
        val first = firstInput.captured

        val secondInput = slot<ArticleAiResultInput>()
        coEvery { resultRecords.find(capture(secondInput)) } returns null
        repository(client).run(
            action = ArticleAiAction.QUESTION,
            article = article("article-cache-key"),
            forceRefresh = false,
            question = "Second question?",
        )
        val second = secondInput.captured

        assertFalse(first.id == second.id)
        assertFalse(first.promptHash == second.promptHash)
        assertEquals(first.contentHash, second.contentHash)
    }

    @Test
    fun `long summary reduces bounded chunk results into final request`() = runTest {
        appPreferences.aiOptions.maxInputCharacters.set(1_000)
        val client = ChunkSummaryAiChatClient(successfulChunks = 3)

        repository(client).run(
            action = ArticleAiAction.SUMMARIZE,
            article = article(
                id = "article-long",
                contentHTML = "<p>${"A".repeat(2_500)}</p>",
            ),
            forceRefresh = true,
        )

        assertEquals(4, client.requests.size)
        val finalPrompt = client.requests.last().messages.last().content
        assertTrue(finalPrompt.contains("chunk-1"))
        assertTrue(finalPrompt.contains("chunk-2"))
        assertTrue(finalPrompt.contains("chunk-3"))
    }

    private fun repository(client: AiChatClient): ArticleAiRepository {
        return ArticleAiRepository(
            context = RuntimeEnvironment.getApplication(),
            appPreferences = appPreferences,
            account = account,
            chatClient = client,
            articleAiDigestRecords = digestRecords,
            articleAiResultRecords = resultRecords,
        )
    }

    private fun article(
        id: String,
        contentHTML: String = "<p>First paragraph.</p>",
    ) = Article(
        id = id,
        feedID = "feed-1",
        title = "Example article",
        author = null,
        contentHTML = contentHTML,
        url = URL("https://example.com/article"),
        summary = "",
        imageURL = null,
        updatedAt = ZonedDateTime.now(),
        publishedAt = ZonedDateTime.now(),
        read = false,
        starred = false,
    )
}

private class ChunkSummaryAiChatClient(
    private val successfulChunks: Int,
) : AiChatClient {
    val requests = mutableListOf<AiChatRequest>()

    override suspend fun complete(request: AiChatRequest): Result<String> {
        requests += request
        return if (requests.size <= successfulChunks) {
            Result.success("chunk-${requests.size}")
        } else {
            Result.failure(AiTransportException(AiTransportErrorReason.PROVIDER_REJECTED))
        }
    }
}

private class RecordingAiChatClient(
    private val result: Result<String>,
) : AiChatClient {
    val requests = mutableListOf<AiChatRequest>()

    override suspend fun complete(request: AiChatRequest): Result<String> {
        requests += request
        return result
    }
}

private fun ArticleAiResultInput.toRecord(resultText: String) = ArticleAiResultRecord(
    id = id,
    articleID = articleID,
    action = action,
    provider = provider,
    baseURL = baseURL,
    model = model,
    language = language,
    promptHash = promptHash,
    contentHash = contentHash,
    resultText = resultText,
    createdAt = 0,
    updatedAt = 0,
)
