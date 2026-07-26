package com.capyreader.app.ai

import androidx.preference.PreferenceManager
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.preferences.InMemorySecretStore
import com.jocmp.capy.Account
import com.jocmp.capy.Article
import com.jocmp.capy.ArticleAutomation
import com.jocmp.capy.ArticleAutomationRule
import com.jocmp.capy.ArticleAutomationResult
import com.jocmp.capy.ArticleRuleAction
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
class ArticleAiRuleProcessorTest {
    private lateinit var preferences: AppPreferences
    private lateinit var budget: ArticleAiRuleDailyBudget

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .clear()
            .commit()
        preferences = AppPreferences(context, InMemorySecretStore())
        preferences.clearAll()
        preferences.aiOptions.ruleEvaluationsDailyLimit.set(10)
        budget = ArticleAiRuleDailyBudget(preferences.aiOptions) {
            LocalDate.of(2026, 7, 25)
        }
    }

    @Test
    fun `default-off rules perform zero evaluation work`() = runTest {
        val source = FakeDecisionSource()
        val matches = mutableListOf<String>()
        val processor = processor(source, matches)

        val run = processor.process(
            rules = listOf(
                ArticleAutomationRule(
                    actions = setOf(ArticleRuleAction.STAR),
                )
            ),
            articles = listOf(article("one")),
            maxRequests = 10,
        )

        assertEquals(ArticleAiRuleRun(), run)
        assertEquals(0, source.cacheChecks)
        assertEquals(0, source.requests)
    }

    @Test
    fun `ineligible feeds consume no provider or daily budget`() = runTest {
        val source = FakeDecisionSource().apply {
            ineligible += "excluded"
        }

        val run = processor(source, mutableListOf()).process(
            rules = listOf(aiRule()),
            articles = listOf(article("excluded")),
            maxRequests = 10,
        )

        assertEquals(0, run.requested)
        assertEquals(0, source.cacheChecks)
        assertEquals(0, source.requests)
        assertEquals(10, budget.remaining())
    }

    @Test
    fun `AI rule work defaults to unmetered network`() {
        assertEquals(
            NetworkType.UNMETERED,
            articleAiRuleConstraints().requiredNetworkType,
        )
    }

    @Test
    fun `dedup failure isolation and explanations remain bounded`() = runTest {
        val source = FakeDecisionSource().apply {
            cached["cached|rule-1"] = ArticleAiRuleDecision(false, "Already evaluated.")
            failures += "failed"
            decisions["matched"] = ArticleAiRuleDecision(true, "High-priority release.")
            decisions["not-matched"] = ArticleAiRuleDecision(false, "Routine update.")
        }
        val matches = mutableListOf<String>()
        val run = processor(source, matches).process(
            rules = listOf(aiRule()),
            articles = listOf(
                article("cached"),
                article("failed"),
                article("matched"),
                article("not-matched"),
            ),
            maxRequests = 10,
        )

        assertEquals(1, run.cached)
        assertEquals(4, run.candidatesChecked)
        assertEquals(3, run.requested)
        assertEquals(2, run.evaluated)
        assertEquals(1, run.matched)
        assertEquals(listOf("failed"), run.failures.map { it.articleID })
        assertEquals(listOf("matched:High-priority release."), matches)
        assertTrue("matched|rule-1" in source.cached)
        assertTrue("not-matched|rule-1" in source.cached)
        assertEquals(7, budget.remaining())
    }

    @Test
    fun `cached mute decision reapplies without another provider request`() = runTest {
        val source = FakeDecisionSource().apply {
            cached["recreated|rule-1"] = ArticleAiRuleDecision(
                matches = true,
                explanation = "Still matches the mute criterion.",
            )
        }
        val matches = mutableListOf<String>()

        val run = processor(source, matches).process(
            rules = listOf(aiRule(actions = setOf(ArticleRuleAction.MUTE))),
            articles = listOf(article("recreated")),
            maxRequests = 10,
        )

        assertEquals(1, run.cached)
        assertEquals(1, run.matched)
        assertEquals(0, run.requested)
        assertEquals(
            listOf("recreated:Still matches the mute criterion."),
            matches,
        )
        assertEquals(10, budget.remaining())
    }

    @Test
    fun `failed provider calls count toward per-run and daily cost limits`() = runTest {
        preferences.aiOptions.ruleEvaluationsDailyLimit.set(2)
        val source = FakeDecisionSource().apply {
            failures += listOf("one", "two", "three", "four")
        }

        val run = processor(source, mutableListOf()).process(
            rules = listOf(aiRule()),
            articles = listOf(
                article("one"),
                article("two"),
                article("three"),
                article("four"),
            ),
            maxRequests = 3,
        )

        assertEquals(2, run.requested)
        assertEquals(2, run.candidatesChecked)
        assertEquals(2, run.failures.size)
        assertEquals(0, budget.remaining())
        assertEquals(2, source.requests)
    }

    @Test
    fun `valid provider decision is counted when its action fails`() = runTest {
        val source = FakeDecisionSource().apply {
            decisions["one"] = ArticleAiRuleDecision(true, "Matches.")
        }
        val processor = ArticleAiRuleProcessor(
            decisionSource = source,
            dailyBudget = budget,
            matchApplier = ArticleAiRuleMatchHandler { _, _, _ ->
                error("action failure")
            },
        )

        val run = processor.process(
            rules = listOf(aiRule()),
            articles = listOf(article("one")),
            maxRequests = 10,
        )

        assertEquals(1, run.evaluated)
        assertEquals(0, run.matched)
        assertEquals(1, run.failures.size)
    }

    @Test
    fun `matched read and star actions enter account sync path before local actions`() = runTest {
        val account = mockk<Account>()
        val automation = mockk<ArticleAutomation>()
        val rule = aiRule(
            actions = setOf(
                ArticleRuleAction.MARK_READ,
                ArticleRuleAction.STAR,
            )
        )
        val article = article("remote")
        val decision = ArticleAiRuleDecision(true, "Urgent release.")
        val result = ArticleAutomationResult(markRead = true, star = true)
        every {
            automation.resultForAiMatch(rule, decision.explanation)
        } returns result
        coEvery { account.markRead(article.id) } returns Result.success(Unit)
        coEvery { account.addStar(article.id) } returns Result.success(Unit)
        every {
            automation.applyLocalActions(article.id, result, any())
        } returns Unit

        ArticleAiRuleMatchApplier(account, automation)
            .apply(rule, article, decision)

        coVerifyOrder {
            account.markRead(article.id)
            account.addStar(article.id)
            automation.applyLocalActions(article.id, result, any())
        }
    }

    @Test
    fun `disabled and missing-key configurations enqueue no work lazily`() {
        val context = RuntimeEnvironment.getApplication()
        var rulesRead = false

        assertEquals(
            ArticleAiRuleRunAvailability.AI_DISABLED,
            ArticleAiRuleWorker.enqueueIfEligible(
                context = context,
                appPreferences = preferences,
                rules = {
                    rulesRead = true
                    listOf(aiRule())
                },
            ),
        )
        assertFalse(rulesRead)

        preferences.aiOptions.enabled.set(true)
        assertEquals(
            ArticleAiRuleRunAvailability.API_KEY_REQUIRED,
            ArticleAiRuleWorker.enqueueIfEligible(
                context = context,
                appPreferences = preferences,
                rules = {
                    rulesRead = true
                    listOf(aiRule())
                },
            ),
        )
        assertFalse(rulesRead)
        assertTrue(aiRuleWorkInfos().isEmpty())
    }

    @Test
    fun `manual scheduling keeps one bounded work request`() {
        configureProvider()
        val context = RuntimeEnvironment.getApplication()

        repeat(2) {
            assertEquals(
                ArticleAiRuleRunAvailability.READY,
                ArticleAiRuleWorker.enqueueIfEligible(
                    context = context,
                    appPreferences = preferences,
                    rules = { listOf(aiRule()) },
                ),
            )
        }

        val work = aiRuleWorkInfos().single()
        assertEquals(WorkInfo.State.ENQUEUED, work.state)
        assertEquals(
            ArticleAiRuleRunState.QUEUED,
            ArticleAiRuleRunStatus.deserialize(
                preferences.aiOptions.ruleEvaluationRunStatus.get()
            ).state,
        )
    }

    @Test
    fun `daily allowance and retry attempts remain bounded`() {
        configureProvider()
        preferences.aiOptions.ruleEvaluationsDailyLimit.set(1)
        assertTrue(
            ArticleAiRuleDailyBudget(preferences.aiOptions).recordAttempt()
        )

        assertEquals(
            ArticleAiRuleRunAvailability.DAILY_LIMIT_REACHED,
            ArticleAiRuleWorker.enqueueIfEligible(
                context = RuntimeEnvironment.getApplication(),
                appPreferences = preferences,
                rules = { listOf(aiRule()) },
            ),
        )
        assertTrue(aiRuleWorkInfos().isEmpty())
        assertTrue(shouldRetryArticleAiRuleWork(runAttemptCount = 0))
        assertTrue(shouldRetryArticleAiRuleWork(runAttemptCount = 1))
        assertFalse(shouldRetryArticleAiRuleWork(runAttemptCount = 2))
    }

    @Test
    fun `availability distinguishes missing and disabled AI rules`() {
        configureProvider()

        assertEquals(
            ArticleAiRuleRunAvailability.NO_AI_RULES,
            articleAiRuleRunAvailability(preferences.aiOptions, emptyList()),
        )
        assertEquals(
            ArticleAiRuleRunAvailability.NO_ENABLED_AI_RULES,
            articleAiRuleRunAvailability(
                preferences.aiOptions,
                listOf(aiRule().copy(enabled = false)),
            ),
        )
    }

    private fun processor(
        source: ArticleAiRuleDecisionSource,
        matches: MutableList<String>,
    ) = ArticleAiRuleProcessor(
        decisionSource = source,
        dailyBudget = budget,
        matchApplier = ArticleAiRuleMatchHandler { _, article, decision ->
            matches += "${article.id}:${decision.explanation}"
        },
    )

    private fun aiRule(
        actions: Set<ArticleRuleAction> = setOf(ArticleRuleAction.STAR),
    ) = ArticleAutomationRule(
        id = "rule-1",
        enabled = true,
        actions = actions,
        aiEnabled = true,
        aiCriterion = "Important engineering news",
    )

    private fun configureProvider() {
        preferences.aiOptions.enabled.set(true)
        preferences.aiOptions.apiKey.set("test-key")
        preferences.aiOptions.model.set("test-model")
    }

    private fun aiRuleWorkInfos(): List<WorkInfo> {
        return WorkManager.getInstance(RuntimeEnvironment.getApplication())
            .getWorkInfosForUniqueWork(ArticleAiRuleWorker.WORK_NAME)
            .get()
    }

    private fun article(id: String) = Article(
        id = id,
        feedID = "feed-1",
        title = "Article $id",
        author = null,
        contentHTML = "<p>Body</p>",
        url = URL("https://example.com/$id"),
        summary = "Summary",
        imageURL = null,
        updatedAt = ZonedDateTime.now(),
        publishedAt = ZonedDateTime.now(),
        read = false,
        starred = false,
        feedName = "Engineering",
    )

    private class FakeDecisionSource : ArticleAiRuleDecisionSource {
        val cached = mutableMapOf<String, ArticleAiRuleDecision>()
        val decisions = mutableMapOf<String, ArticleAiRuleDecision>()
        val failures = mutableSetOf<String>()
        val ineligible = mutableSetOf<String>()
        var cacheChecks = 0
        var requests = 0

        override suspend fun isEligible(
            rule: ArticleAutomationRule,
            article: Article,
        ): Boolean = article.id !in ineligible

        override suspend fun cachedDecision(
            rule: ArticleAutomationRule,
            article: Article,
        ): ArticleAiRuleDecision? {
            cacheChecks += 1
            return cached[key(rule, article)]
        }

        override suspend fun evaluate(
            rule: ArticleAutomationRule,
            article: Article,
        ): Result<ArticleAiRuleDecision> {
            requests += 1
            if (article.id in failures) {
                return Result.failure(IllegalStateException("provider failure"))
            }
            return Result.success(
                decisions[article.id] ?: ArticleAiRuleDecision(false, "No match.")
            )
        }

        override suspend fun recordDecision(
            rule: ArticleAutomationRule,
            article: Article,
            decision: ArticleAiRuleDecision,
        ) {
            cached[key(rule, article)] = decision
        }

        private fun key(rule: ArticleAutomationRule, article: Article): String {
            return "${article.id}|${rule.id}"
        }
    }
}
