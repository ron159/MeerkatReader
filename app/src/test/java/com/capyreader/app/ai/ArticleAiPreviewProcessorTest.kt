package com.capyreader.app.ai

import android.content.Context
import androidx.preference.PreferenceManager
import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.preferences.InMemorySecretStore
import com.jocmp.capy.Article
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.URL
import java.time.LocalDate
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ArticleAiPreviewProcessorTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()
    private lateinit var preferences: AppPreferences
    private lateinit var repository: ArticleAiRepository
    private lateinit var budget: ArticleAiDailyBudget
    private lateinit var processor: ArticleAiPreviewProcessor

    @Before
    fun setUp() {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .clear()
            .commit()
        preferences = AppPreferences(context, InMemorySecretStore())
        preferences.clearAll()
        repository = mockk()
        budget = ArticleAiDailyBudget(preferences.aiOptions) {
            LocalDate.of(2026, 7, 25)
        }
        processor = ArticleAiPreviewProcessor(repository, budget)
        coEvery {
            repository.cachedResult(ArticleAiAction.PREVIEW_SUMMARY, any())
        } returns null
        coEvery {
            repository.run(
                action = ArticleAiAction.PREVIEW_SUMMARY,
                article = any(),
                forceRefresh = true,
            )
        } returns Result.success("preview")
    }

    @Test
    fun `cache hits and failures do not consume daily allowance`() = runTest {
        preferences.aiOptions.backgroundPreviewsDailyLimit.set(2)
        val cached = article("cached")
        val failed = article("failed")
        val firstSuccess = article("success-1")
        val secondSuccess = article("success-2")
        val beyondLimit = article("beyond-limit")
        coEvery {
            repository.cachedResult(ArticleAiAction.PREVIEW_SUMMARY, cached)
        } returns "cached preview"
        coEvery {
            repository.run(
                action = ArticleAiAction.PREVIEW_SUMMARY,
                article = failed,
                forceRefresh = true,
            )
        } returns Result.failure(IllegalStateException("provider failed"))

        val run = processor.process(
            articles = listOf(
                cached,
                failed,
                firstSuccess,
                secondSuccess,
                beyondLimit,
            ),
            maxRequests = 10,
            maxSuccesses = budget.remaining(),
        )

        assertEquals(1, run.cached)
        assertEquals(3, run.requested)
        assertEquals(2, run.generated)
        assertEquals(listOf("failed"), run.failures.map { it.articleID })
        assertEquals(0, budget.remaining())
        coVerify(exactly = 0) {
            repository.run(
                action = ArticleAiAction.PREVIEW_SUMMARY,
                article = cached,
                forceRefresh = true,
            )
        }
        coVerify(exactly = 0) {
            repository.run(
                action = ArticleAiAction.PREVIEW_SUMMARY,
                article = beyondLimit,
                forceRefresh = true,
            )
        }
    }

    @Test
    fun `per-run request cap remains bounded below daily limit`() = runTest {
        preferences.aiOptions.backgroundPreviewsDailyLimit.set(20)
        val articles = (1..12).map { article("article-$it") }

        val run = processor.process(
            articles = articles,
            maxRequests = 10,
            maxSuccesses = budget.remaining(),
        )

        assertEquals(10, run.requested)
        assertEquals(10, run.generated)
        assertEquals(10, budget.remaining())
        coVerify(exactly = 10) {
            repository.run(
                action = ArticleAiAction.PREVIEW_SUMMARY,
                article = any(),
                forceRefresh = true,
            )
        }
    }

    @Test
    fun `failed requests stop at run cap without charging daily budget`() = runTest {
        preferences.aiOptions.backgroundPreviewsDailyLimit.set(20)
        coEvery {
            repository.run(
                action = ArticleAiAction.PREVIEW_SUMMARY,
                article = any(),
                forceRefresh = true,
            )
        } returns Result.failure(IllegalStateException("provider failed"))

        val run = processor.process(
            articles = (1..12).map { article("article-$it") },
            maxRequests = 10,
            maxSuccesses = budget.remaining(),
        )

        assertEquals(10, run.requested)
        assertEquals(0, run.generated)
        assertEquals(10, run.failures.size)
        assertEquals(20, budget.remaining())
    }

    private fun article(id: String) = Article(
        id = id,
        feedID = "feed-1",
        title = "Article $id",
        author = null,
        contentHTML = "<p>Content</p>",
        url = URL("https://example.com/$id"),
        summary = "",
        imageURL = null,
        updatedAt = ZonedDateTime.now(),
        publishedAt = ZonedDateTime.now(),
        read = false,
        starred = false,
    )
}
