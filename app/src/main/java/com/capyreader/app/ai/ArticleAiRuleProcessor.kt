package com.capyreader.app.ai

import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.preferences.DEFAULT_AI_RULE_EVALUATIONS_DAILY_LIMIT
import com.jocmp.capy.Account
import com.jocmp.capy.Article
import com.jocmp.capy.ArticleAutomation
import com.jocmp.capy.ArticleAutomationRule
import com.jocmp.capy.ArticleRuleAction
import kotlinx.coroutines.CancellationException
import java.time.LocalDate

internal class ArticleAiRuleProcessor(
    private val decisionSource: ArticleAiRuleDecisionSource,
    private val dailyBudget: ArticleAiRuleDailyBudget,
    private val matchApplier: ArticleAiRuleMatchHandler,
) {
    suspend fun process(
        rules: List<ArticleAutomationRule>,
        articles: List<Article>,
        maxRequests: Int,
    ): ArticleAiRuleRun {
        val activeRules = rules
            .filter {
                it.enabled &&
                    it.aiEnabled &&
                    it.aiCriterion.isNotBlank() &&
                    it.actions.isNotEmpty()
            }
            .take(MAX_ACTIVE_RULES_PER_RUN)
        if (activeRules.isEmpty() || maxRequests <= 0) {
            return ArticleAiRuleRun()
        }

        var cached = 0
        var requested = 0
        var evaluated = 0
        var matched = 0
        var candidatesChecked = 0
        val failures = mutableListOf<ArticleAiRuleFailure>()

        articleLoop@ for (article in articles.distinctBy { it.id }) {
            if (requested >= maxRequests || dailyBudget.remaining() == 0) {
                break
            }
            candidatesChecked += 1
            for (rule in activeRules) {
                if (requested >= maxRequests || dailyBudget.remaining() == 0) {
                    break@articleLoop
                }

                val eligible = try {
                    decisionSource.isEligible(rule, article)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    failures += ArticleAiRuleFailure(article.id, rule.id, error)
                    continue
                }
                if (!eligible) {
                    continue
                }

                val cachedDecision = try {
                    decisionSource.cachedDecision(rule, article)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    failures += ArticleAiRuleFailure(article.id, rule.id, error)
                    continue
                }
                if (cachedDecision != null) {
                    cached += 1
                    if (
                        cachedDecision.matches &&
                        shouldReapplyCachedMatch(rule, article)
                    ) {
                        try {
                            matchApplier.apply(rule, article, cachedDecision)
                            matched += 1
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            failures += ArticleAiRuleFailure(article.id, rule.id, error)
                        }
                    }
                    continue
                }

                if (!dailyBudget.recordAttempt()) {
                    break@articleLoop
                }
                requested += 1

                val evaluation = try {
                    decisionSource.evaluate(rule, article)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    failures += ArticleAiRuleFailure(article.id, rule.id, error)
                    continue
                }

                evaluation.fold(
                    onSuccess = { decision ->
                        evaluated += 1
                        try {
                            if (decision.matches) {
                                matchApplier.apply(rule, article, decision)
                                matched += 1
                            }
                            decisionSource.recordDecision(rule, article, decision)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            failures += ArticleAiRuleFailure(article.id, rule.id, error)
                        }
                    },
                    onFailure = { error ->
                        if (error is CancellationException) {
                            throw error
                        }
                        failures += ArticleAiRuleFailure(article.id, rule.id, error)
                    },
                )
            }
        }

        return ArticleAiRuleRun(
            candidatesChecked = candidatesChecked,
            cached = cached,
            requested = requested,
            evaluated = evaluated,
            matched = matched,
            failures = failures,
        )
    }

    private fun shouldReapplyCachedMatch(
        rule: ArticleAutomationRule,
        article: Article,
    ): Boolean {
        val actions = rule.actions
        val mutesArticle =
            ArticleRuleAction.MUTE in actions && ArticleRuleAction.KEEP !in actions

        return mutesArticle ||
            ArticleRuleAction.MARK_READ in actions ||
            (ArticleRuleAction.STAR in actions && !article.starred)
    }

    companion object {
        private const val MAX_ACTIVE_RULES_PER_RUN = 10
    }
}

internal fun interface ArticleAiRuleMatchHandler {
    suspend fun apply(
        rule: ArticleAutomationRule,
        article: Article,
        decision: ArticleAiRuleDecision,
    )
}

internal class ArticleAiRuleMatchApplier(
    private val account: Account,
    private val articleAutomation: ArticleAutomation,
) : ArticleAiRuleMatchHandler {
    override suspend fun apply(
        rule: ArticleAutomationRule,
        article: Article,
        decision: ArticleAiRuleDecision,
    ) {
        val result = articleAutomation.resultForAiMatch(
            rule = rule,
            explanation = decision.explanation,
        )

        if (result.mute) {
            account.markRead(article.id).getOrThrow()
            articleAutomation.logMatches(
                articleID = article.id,
                result = result,
            )
            articleAutomation.clearMutedArticle(article.id)
            return
        }

        if (result.shouldMarkReadRemotely) {
            account.markRead(article.id).getOrThrow()
        }
        if (result.star) {
            account.addStar(article.id).getOrThrow()
        }

        articleAutomation.applyLocalActions(
            articleID = article.id,
            result = result,
        )
    }
}

internal class ArticleAiRuleDailyBudget(
    private val aiOptions: AppPreferences.AiOptions,
    private val today: () -> LocalDate = LocalDate::now,
) {
    @Synchronized
    fun remaining(): Int {
        val limit = aiOptions.ruleEvaluationsDailyLimit.get()
            .coerceIn(MIN_DAILY_LIMIT, MAX_DAILY_LIMIT)
        return (limit - currentUsage().count).coerceAtLeast(0)
    }

    @Synchronized
    fun recordAttempt(): Boolean {
        val limit = aiOptions.ruleEvaluationsDailyLimit.get()
            .coerceIn(MIN_DAILY_LIMIT, MAX_DAILY_LIMIT)
        val usage = currentUsage()
        if (usage.count >= limit) {
            return false
        }

        aiOptions.ruleEvaluationsDailyUsage.set(
            DailyUsage(
                day = usage.day,
                count = usage.count + 1,
            ).serialize()
        )
        return true
    }

    private fun currentUsage(): DailyUsage {
        val currentDay = today()
        val stored = DailyUsage.parse(aiOptions.ruleEvaluationsDailyUsage.get())
        if (stored?.day == currentDay) {
            return stored.copy(count = stored.count.coerceAtLeast(0))
        }

        return DailyUsage(day = currentDay, count = 0).also {
            aiOptions.ruleEvaluationsDailyUsage.set(it.serialize())
        }
    }

    private data class DailyUsage(
        val day: LocalDate,
        val count: Int,
    ) {
        fun serialize(): String = "$day|$count"

        companion object {
            fun parse(value: String): DailyUsage? {
                val parts = value.split('|', limit = 2)
                if (parts.size != 2) {
                    return null
                }
                val day = runCatching { LocalDate.parse(parts[0]) }.getOrNull()
                    ?: return null
                val count = parts[1].toIntOrNull() ?: return null
                return DailyUsage(day, count)
            }
        }
    }

    companion object {
        const val DEFAULT_DAILY_LIMIT = DEFAULT_AI_RULE_EVALUATIONS_DAILY_LIMIT
        const val MIN_DAILY_LIMIT = 1
        const val MAX_DAILY_LIMIT = 100
    }
}

internal data class ArticleAiRuleRun(
    val candidatesChecked: Int = 0,
    val cached: Int = 0,
    val requested: Int = 0,
    val evaluated: Int = 0,
    val matched: Int = 0,
    val failures: List<ArticleAiRuleFailure> = emptyList(),
)

internal data class ArticleAiRuleFailure(
    val articleID: String,
    val ruleID: String,
    val error: Throwable,
)
