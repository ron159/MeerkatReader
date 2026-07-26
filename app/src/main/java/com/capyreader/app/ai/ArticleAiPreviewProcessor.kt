package com.capyreader.app.ai

import com.jocmp.capy.Article

internal class ArticleAiPreviewProcessor(
    private val articleAiRepository: ArticleAiRepository,
    private val dailyBudget: ArticleAiDailyBudget,
) {
    suspend fun process(
        articles: List<Article>,
        maxRequests: Int,
        maxSuccesses: Int,
    ): ArticleAiPreviewRun {
        var cached = 0
        var requested = 0
        var generated = 0
        var budgetExhausted = false
        val failures = mutableListOf<ArticleAiPreviewFailure>()

        for (article in articles) {
            if (
                requested >= maxRequests ||
                generated >= maxSuccesses ||
                budgetExhausted
            ) {
                break
            }

            if (!articleAiRepository.cachedResult(
                    ArticleAiAction.PREVIEW_SUMMARY,
                    article,
                ).isNullOrBlank()
            ) {
                cached += 1
                continue
            }

            requested += 1
            articleAiRepository.run(
                action = ArticleAiAction.PREVIEW_SUMMARY,
                article = article,
                forceRefresh = true,
            ).onSuccess {
                if (dailyBudget.recordSuccess()) {
                    generated += 1
                } else {
                    budgetExhausted = true
                }
            }.onFailure { error ->
                failures += ArticleAiPreviewFailure(
                    articleID = article.id,
                    error = error,
                )
            }
        }

        return ArticleAiPreviewRun(
            cached = cached,
            requested = requested,
            generated = generated,
            failures = failures,
        )
    }
}

internal data class ArticleAiPreviewRun(
    val cached: Int,
    val requested: Int,
    val generated: Int,
    val failures: List<ArticleAiPreviewFailure>,
)

internal data class ArticleAiPreviewFailure(
    val articleID: String,
    val error: Throwable,
)
