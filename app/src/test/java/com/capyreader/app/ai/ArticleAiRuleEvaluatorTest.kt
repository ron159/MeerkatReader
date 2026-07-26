package com.capyreader.app.ai

import androidx.preference.PreferenceManager
import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.preferences.InMemorySecretStore
import com.jocmp.capy.Account
import com.jocmp.capy.Article
import com.jocmp.capy.ArticleAutomationRule
import com.jocmp.capy.ArticleRuleAction
import com.jocmp.capy.Feed
import com.jocmp.capy.persistence.ArticleAiResultInput
import com.jocmp.capy.persistence.ArticleAiResultRecords
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.koin.core.context.stopKoin
import java.net.URL
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
class ArticleAiRuleEvaluatorTest {
    private lateinit var preferences: AppPreferences
    private lateinit var account: Account
    private lateinit var records: ArticleAiResultRecords

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .clear()
            .commit()
        preferences = AppPreferences(context, InMemorySecretStore()).also {
            it.aiOptions.enabled.set(true)
            it.aiOptions.baseUrl.set("https://provider.example/v1")
            it.aiOptions.apiKey.set("secret-key")
            it.aiOptions.model.set("test-model")
            it.aiOptions.language.set("English")
        }
        account = mockk()
        records = mockk(relaxed = true)
        coEvery { account.findFeed(any()) } returns null
        coEvery { records.find(any()) } returns null
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `default-off rule never calls provider`() = runTest {
        val client = RecordingRuleClient(Result.success(validResponse()))
        val evaluator = evaluator(client)

        val result = evaluator.evaluate(
            rule = ArticleAutomationRule(
                actions = setOf(ArticleRuleAction.STAR),
            ),
            article = article(),
        )

        assertTrue(result.isFailure)
        assertEquals(
            ArticleAiErrorReason.DISABLED,
            (result.exceptionOrNull() as ArticleAiException).reason,
        )
        assertTrue(client.requests.isEmpty())
        coVerify(exactly = 0) { records.upsert(any(), any()) }
    }

    @Test
    fun `feed AI exclusion prevents eligibility and provider calls`() = runTest {
        val excludedFeed = mockk<Feed>()
        every { excludedFeed.excludeFromAi } returns true
        coEvery { account.findFeed("feed-1") } returns excludedFeed
        val client = RecordingRuleClient(Result.success(validResponse()))
        val evaluator = evaluator(client)

        assertFalse(evaluator.isEligible(aiRule(), article()))
        val result = evaluator.evaluate(aiRule(), article())

        assertEquals(
            ArticleAiErrorReason.DISABLED_FOR_FEED,
            (result.exceptionOrNull() as ArticleAiException).reason,
        )
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun `request contains only bounded article metadata and strict response parses`() = runTest {
        val client = RecordingRuleClient(Result.success(validResponse()))
        val evaluator = evaluator(client)
        val rule = aiRule(
            criterion = "C".repeat(ArticleAiRuleEvaluator.MAX_CRITERION_CHARACTERS + 40)
        )
        val article = article(
            title = "T".repeat(ArticleAiRuleEvaluator.MAX_TITLE_CHARACTERS + 40),
            author = "A".repeat(ArticleAiRuleEvaluator.MAX_AUTHOR_CHARACTERS + 40),
            summary = "S".repeat(ArticleAiRuleEvaluator.MAX_SUMMARY_CHARACTERS + 40),
            contentHTML = "<p>FULL-CONTENT-SECRET</p>",
        )

        val decision = evaluator.evaluate(rule, article).getOrThrow()

        assertTrue(decision.matches)
        assertEquals("Matches the requested topic.", decision.explanation)
        val request = client.requests.single()
        val payloadText = request.messages.last().content
        val payload = Json.parseToJsonElement(payloadText).jsonObject
        val metadata = payload.getValue("metadata").jsonObject
        assertEquals(setOf("criterion", "metadata"), payload.keys)
        assertEquals(setOf("title", "feed", "author", "summary"), metadata.keys)
        assertEquals(
            ArticleAiRuleEvaluator.MAX_CRITERION_CHARACTERS,
            payload.getValue("criterion").jsonPrimitive.content.length,
        )
        assertEquals(
            ArticleAiRuleEvaluator.MAX_TITLE_CHARACTERS,
            metadata.getValue("title").jsonPrimitive.content.length,
        )
        assertEquals(
            ArticleAiRuleEvaluator.MAX_AUTHOR_CHARACTERS,
            metadata.getValue("author").jsonPrimitive.content.length,
        )
        assertEquals(
            ArticleAiRuleEvaluator.MAX_SUMMARY_CHARACTERS,
            metadata.getValue("summary").jsonPrimitive.content.length,
        )
        assertFalse(payloadText.contains("FULL-CONTENT-SECRET"))
        assertFalse(payloadText.contains(article.url.toString()))
    }

    @Test
    fun `malformed oversized and ambiguous responses are rejected with redacted reason`() = runTest {
        val invalidResponses = listOf(
            "",
            "```json\n${validResponse()}\n```",
            """{"matches":true,"explanation":"ok","extra":"not allowed"}""",
            """{"matches":"true","explanation":"wrong type"}""",
            """{"matches":true,"matches":false,"explanation":"ambiguous"}""",
            """{"matches":true,"explanation":7}""",
            """{"matches":true,"explanation":""}""",
            """{"matches":true,"explanation":"${"x".repeat(501)}"}""",
            validResponse() + "x".repeat(ArticleAiRuleEvaluator.MAX_RESPONSE_CHARACTERS),
        )

        invalidResponses.forEach { response ->
            val result = evaluator(RecordingRuleClient(Result.success(response)))
                .evaluate(aiRule(), article())
            val error = result.exceptionOrNull() as ArticleAiException

            assertEquals(ArticleAiErrorReason.INVALID_RESPONSE, error.reason)
            assertEquals("INVALID_RESPONSE", error.message)
        }
    }

    @Test
    fun `recorded decisions use rule and metadata hashes for deduplication`() = runTest {
        val inputs = mutableListOf<ArticleAiResultInput>()
        val evaluator = evaluator(RecordingRuleClient(Result.success(validResponse())))
        val firstRule = aiRule(criterion = "Security")
        val firstArticle = article(summary = "Patch released")

        evaluator.recordDecision(
            rule = firstRule,
            article = firstArticle,
            decision = ArticleAiRuleDecision(true, "Security patch."),
        )
        evaluator.recordDecision(
            rule = firstRule.copy(aiCriterion = "Design"),
            article = firstArticle,
            decision = ArticleAiRuleDecision(false, "Not a design article."),
        )
        evaluator.recordDecision(
            rule = firstRule,
            article = firstArticle.copy(summary = "Patch delayed"),
            decision = ArticleAiRuleDecision(true, "Security delay."),
        )
        evaluator.recordDecision(
            rule = firstRule.copy(actions = setOf(ArticleRuleAction.MARK_READ)),
            article = firstArticle,
            decision = ArticleAiRuleDecision(true, "Mark this read."),
        )
        coVerify(exactly = 4) {
            records.upsert(capture(inputs), any())
        }
        val (first, changedCriterion, changedContent, changedActions) = inputs

        assertTrue(first.action.contains(firstRule.id))
        assertFalse(first.promptHash == changedCriterion.promptHash)
        assertFalse(first.contentHash == changedContent.contentHash)
        assertFalse(first.promptHash == changedActions.promptHash)
    }

    private fun evaluator(client: AiChatClient) = ArticleAiRuleEvaluator(
        appPreferences = preferences,
        account = account,
        chatClient = client,
        resultRecords = records,
    )

    private fun aiRule(
        criterion: String = "Important engineering news",
    ) = ArticleAutomationRule(
        id = "rule-1",
        enabled = true,
        actions = setOf(ArticleRuleAction.STAR),
        aiEnabled = true,
        aiCriterion = criterion,
    )

    private fun article(
        title: String = "Example article",
        author: String? = "Ada",
        summary: String = "Summary",
        contentHTML: String = "<p>Body</p>",
    ) = Article(
        id = "article-1",
        feedID = "feed-1",
        title = title,
        author = author,
        contentHTML = contentHTML,
        url = URL("https://example.com/article"),
        summary = summary,
        imageURL = null,
        updatedAt = ZonedDateTime.now(),
        publishedAt = ZonedDateTime.now(),
        read = false,
        starred = false,
        feedName = "F".repeat(ArticleAiRuleEvaluator.MAX_FEED_CHARACTERS + 40),
    )

    private fun validResponse() = """
        {"matches":true,"explanation":"Matches the requested topic."}
    """.trimIndent()

    private class RecordingRuleClient(
        var response: Result<String>,
    ) : AiChatClient {
        val requests = mutableListOf<AiChatRequest>()

        override suspend fun complete(request: AiChatRequest): Result<String> {
            requests += request
            return response
        }
    }
}
