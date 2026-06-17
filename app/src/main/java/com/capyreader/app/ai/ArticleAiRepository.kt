package com.capyreader.app.ai

import android.content.Context
import com.capyreader.app.preferences.AiProvider
import com.capyreader.app.preferences.AppPreferences
import com.jocmp.capy.Account
import com.jocmp.capy.Article
import com.jocmp.capy.common.withIOContext
import com.jocmp.capy.persistence.ArticleAiDigestInput
import com.jocmp.capy.persistence.ArticleAiDigestRecords
import com.jocmp.capy.persistence.ArticleAiResultInput
import com.jocmp.capy.persistence.ArticleAiResultRecords
import java.io.File
import java.security.MessageDigest

class ArticleAiRepository(
    context: Context,
    private val appPreferences: AppPreferences,
    private val account: Account,
    private val chatClient: AiChatClient,
    private val articleAiDigestRecords: ArticleAiDigestRecords,
    private val articleAiResultRecords: ArticleAiResultRecords,
) {
    private val cacheDirectory = File(context.filesDir, "article-ai-cache")

    suspend fun run(
        action: ArticleAiAction,
        article: Article,
        forceRefresh: Boolean,
        question: String? = null,
    ): Result<String> = withIOContext {
        val settings = ArticleAiSettings.from(appPreferences)
        val fullContent = article.plainTextContent()
        val requestContent = fullContent.take(settings.maxInputCharacters)
        val cacheContent = if (action == ArticleAiAction.SUMMARIZE) {
            fullContent
        } else {
            requestContent
        }
        val questionText = question?.trim().orEmpty()

        if (!settings.enabled) {
            return@withIOContext Result.failure(ArticleAiException(ArticleAiErrorReason.DISABLED))
        }

        if (isExcludedFromAi(article)) {
            return@withIOContext Result.failure(ArticleAiException(ArticleAiErrorReason.DISABLED_FOR_FEED))
        }

        if (settings.apiKey.isBlank()) {
            return@withIOContext Result.failure(ArticleAiException(ArticleAiErrorReason.API_KEY_REQUIRED))
        }

        if (settings.model.isBlank()) {
            return@withIOContext Result.failure(ArticleAiException(ArticleAiErrorReason.MODEL_REQUIRED))
        }

        if (fullContent.isBlank()) {
            return@withIOContext Result.failure(ArticleAiException(ArticleAiErrorReason.CONTENT_EMPTY))
        }

        if (action == ArticleAiAction.QUESTION && questionText.isBlank()) {
            return@withIOContext Result.failure(ArticleAiException(ArticleAiErrorReason.QUESTION_REQUIRED))
        }

        if (!forceRefresh) {
            cachedResult(settings, action, article, cacheContent, questionText)?.let { cached ->
                return@withIOContext Result.success(cached)
            }
        }

        val result = if (action == ArticleAiAction.SUMMARIZE) {
            requestSummary(settings, article, fullContent)
        } else {
            requestAi(settings, action, article, requestContent, questionText)
        }

        result.onSuccess { result ->
            cacheDirectory.mkdirs()
            cacheFile(settings, action, article.id, cacheContent, questionText).writeText(result)
            articleAiResultRecords.upsert(
                input = resultInput(settings, action, article.id, cacheContent, questionText),
                resultText = result,
            )
        }
    }

    suspend fun cachedResult(action: ArticleAiAction, article: Article): String? = withIOContext {
        if (isExcludedFromAi(article)) {
            return@withIOContext null
        }

        val settings = ArticleAiSettings.from(appPreferences)
        val content = article.plainTextContent().take(settings.maxInputCharacters)
        cachedResult(settings, action, article, content)
    }

    suspend fun runDigest(
        articles: List<Article>,
        forceRefresh: Boolean = false,
    ): Result<String> = withIOContext {
        val settings = ArticleAiSettings.from(appPreferences)

        if (!settings.enabled) {
            return@withIOContext Result.failure(ArticleAiException(ArticleAiErrorReason.DISABLED))
        }

        if (settings.apiKey.isBlank()) {
            return@withIOContext Result.failure(ArticleAiException(ArticleAiErrorReason.API_KEY_REQUIRED))
        }

        if (settings.model.isBlank()) {
            return@withIOContext Result.failure(ArticleAiException(ArticleAiErrorReason.MODEL_REQUIRED))
        }

        val eligibleArticles = articles
            .distinctBy { it.id }
            .filterNot { isExcludedFromAi(it) }
            .take(MAX_DIGEST_ARTICLES)

        if (eligibleArticles.isEmpty()) {
            return@withIOContext Result.failure(ArticleAiException(ArticleAiErrorReason.NO_DIGEST_ARTICLES))
        }

        val content = digestInput(eligibleArticles).take(settings.maxInputCharacters)

        if (content.isBlank()) {
            return@withIOContext Result.failure(ArticleAiException(ArticleAiErrorReason.CONTENT_EMPTY))
        }

        val digestInput = digestResultInput(
            settings = settings,
            articles = eligibleArticles,
            content = content,
        )

        if (!forceRefresh) {
            articleAiDigestRecords.find(digestInput.id)?.resultText?.trim()?.takeIf { it.isNotBlank() }?.let { cached ->
                return@withIOContext Result.success(cached)
            }
        }

        chatClient.complete(
            AiChatRequest(
                baseUrl = settings.baseUrl,
                apiKey = settings.apiKey,
                model = settings.model,
                messages = listOf(
                    AiChatMessage(
                        role = "system",
                        content = "You help RSS reader users process article queues. Use only the provided article excerpts.",
                    ),
                    AiChatMessage(
                        role = "user",
                        content = settings.promptFor(ArticleAiAction.DIGEST)
                            .renderPromptVariables(settings.language, eligibleArticles.first(), content, ""),
                    ),
                ),
            )
        ).onSuccess { result ->
            articleAiDigestRecords.upsert(
                input = digestInput,
                resultText = result,
            )
        }
    }

    suspend fun clearCache() = withIOContext {
        articleAiDigestRecords.deleteAll()
        articleAiResultRecords.deleteAll()
        cacheDirectory.deleteRecursively()
    }

    private suspend fun cachedResult(
        settings: ArticleAiSettings,
        action: ArticleAiAction,
        article: Article,
        content: String,
        question: String = "",
    ): String? {
        if (content.isBlank()) {
            return null
        }

        return cachedResult(settings, action, article.id, content, question)
    }

    private suspend fun cachedResult(
        settings: ArticleAiSettings,
        action: ArticleAiAction,
        articleID: String,
        content: String,
        question: String = "",
    ): String? {
        val input = resultInput(settings, action, articleID, content, question)
        val sqlCached = articleAiResultRecords.find(input)?.resultText?.trim()

        if (!sqlCached.isNullOrBlank()) {
            return sqlCached
        }

        val fileCached = cacheFile(settings, action, articleID, content, question)
            .takeIf { it.exists() }
            ?.readText()
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        if (fileCached != null) {
            articleAiResultRecords.upsert(input, fileCached)
        }

        return fileCached
    }

    private suspend fun requestAi(
        settings: ArticleAiSettings,
        action: ArticleAiAction,
        article: Article,
        content: String,
        question: String,
    ): Result<String> {
        return chatClient.complete(
            AiChatRequest(
                baseUrl = settings.baseUrl,
                apiKey = settings.apiKey,
                model = settings.model,
                messages = listOf(
                    AiChatMessage(
                        role = "system",
                        content = "You help RSS reader users understand articles. Use only the provided article content. Return only the requested output without extra preamble, caveats, or source labels.",
                    ),
                    AiChatMessage(
                        role = "user",
                        content = promptFor(settings, action, article, content, question),
                    ),
                ),
            )
        )
    }

    private suspend fun requestSummary(
        settings: ArticleAiSettings,
        article: Article,
        content: String,
    ): Result<String> {
        if (content.length <= settings.maxInputCharacters) {
            return requestAi(settings, ArticleAiAction.SUMMARIZE, article, content, "")
        }

        return requestChunkedSummary(settings, article, content)
    }

    private suspend fun requestChunkedSummary(
        settings: ArticleAiSettings,
        article: Article,
        content: String,
    ): Result<String> {
        val chunks = content.chunked(settings.maxInputCharacters).take(MAX_SUMMARY_CHUNKS)
        val chunkSummaries = mutableListOf<String>()

        chunks.forEachIndexed { index, chunk ->
            val prompt = """
                |Summarize chunk ${index + 1} of ${chunks.size} from this RSS article in ${settings.language}.
                |
                |Return only concise notes that preserve facts, names, numbers, dates, decisions, and consequences.
                |Do not add outside knowledge.
                |
                |Title: ${article.title}
                |URL: ${article.url ?: ""}
                |
                |Chunk:
                |$chunk
            """.trimMargin()

            chatClient.complete(
                AiChatRequest(
                    baseUrl = settings.baseUrl,
                    apiKey = settings.apiKey,
                    model = settings.model,
                    messages = listOf(
                        AiChatMessage(
                            role = "system",
                            content = "You summarize long RSS articles from partial excerpts. Use only the provided chunk.",
                        ),
                        AiChatMessage(role = "user", content = prompt),
                    ),
                )
            ).fold(
                onSuccess = { chunkSummaries += it.trim() },
                onFailure = { return Result.failure(it) },
            )
        }

        val combinedSummaryInput = chunkSummaries.joinToString("\n\n")
        val finalPrompt = """
            |Create the final article summary in ${settings.language} from these chunk summaries.
            |
            |Output:
            |- Return 2 to 3 short paragraphs.
            |- Focus on the main claim/event, why it matters, and important details.
            |- Include concrete names, numbers, dates, and places when they are central.
            |- Use only the chunk summaries. Do not speculate.
            |- Return only the summary, with no heading or preamble.
            |
            |Title: ${article.title}
            |URL: ${article.url ?: ""}
            |
            |Chunk summaries:
            |$combinedSummaryInput
        """.trimMargin()

        return chatClient.complete(
            AiChatRequest(
                baseUrl = settings.baseUrl,
                apiKey = settings.apiKey,
                model = settings.model,
                messages = listOf(
                    AiChatMessage(
                        role = "system",
                        content = "You combine chunk summaries into one faithful RSS article summary.",
                    ),
                    AiChatMessage(role = "user", content = finalPrompt),
                ),
            )
        )
    }

    private suspend fun isExcludedFromAi(article: Article): Boolean {
        return account.findFeed(article.feedID)?.excludeFromAi == true
    }

    private fun digestInput(articles: List<Article>): String {
        return articles.joinToString("\n\n") { article ->
            """
                |Title: ${article.title}
                |Feed: ${article.feedName}
                |Author: ${article.author.orEmpty()}
                |URL: ${article.url ?: ""}
                |Excerpt:
                |${article.plainTextContent().take(MAX_DIGEST_ARTICLE_CHARACTERS)}
            """.trimMargin()
        }
    }

    private fun promptFor(
        settings: ArticleAiSettings,
        action: ArticleAiAction,
        article: Article,
        content: String,
        question: String,
    ): String {
        val template = settings.promptFor(action)
        val renderedTemplate = template.renderPromptVariables(settings.language, article, content, question)

        return if (template.contains("{content}")) {
            renderedTemplate
        } else if (action == ArticleAiAction.TRANSLATE) {
            """
                |$renderedTemplate
                |
                |Return only the translated article body. Do not include the title, URL, labels, explanations, or original text.
                |
                |$content
            """.trimMargin()
        } else {
            """
                |$renderedTemplate
                |
                |Title: ${article.title}
                |URL: ${article.url ?: ""}
                |
                |Article:
                |$content
            """.trimMargin()
        }
    }

    private fun String.renderPromptVariables(
        language: String,
        article: Article,
        content: String,
        question: String,
    ): String {
        return replace("{language}", language)
            .replace("{title}", article.title)
            .replace("{url}", article.url?.toString().orEmpty())
            .replace("{question}", question)
            .replace("{content}", content)
    }

    private fun cacheFile(
        settings: ArticleAiSettings,
        action: ArticleAiAction,
        articleID: String,
        content: String,
        question: String,
    ): File {
        val key = listOf(
            articleID,
            action.name,
            settings.provider.name,
            settings.baseUrl,
            settings.model,
            settings.language,
            settings.promptFor(action),
            question,
            sha256(content),
        ).joinToString("|")

        return File(cacheDirectory, "${sha256(key)}.txt")
    }

    private fun resultInput(
        settings: ArticleAiSettings,
        action: ArticleAiAction,
        articleID: String,
        content: String,
        question: String,
    ): ArticleAiResultInput {
        val promptHash = sha256(listOf(settings.promptFor(action), question).joinToString("|"))
        val contentHash = sha256(content)
        val id = sha256(
            listOf(
                articleID,
                action.name,
                settings.provider.name,
                settings.baseUrl,
                settings.model,
                settings.language,
                promptHash,
                contentHash,
            ).joinToString("|")
        )

        return ArticleAiResultInput(
            id = id,
            articleID = articleID,
            action = action.name,
            provider = settings.provider.name,
            baseURL = settings.baseUrl,
            model = settings.model,
            language = settings.language,
            promptHash = promptHash,
            contentHash = contentHash,
        )
    }

    private fun digestResultInput(
        settings: ArticleAiSettings,
        articles: List<Article>,
        content: String,
    ): ArticleAiDigestInput {
        val articleIDs = articles.map { it.id }
        val articleIdsJson = articleIDs.joinToString(prefix = "[", postfix = "]") { "\"${it.escapeJson()}\"" }
        val filterJson = """{"source":"visible_articles","maxArticles":$MAX_DIGEST_ARTICLES}"""
        val contentHash = sha256(content)
        val promptHash = sha256(settings.promptFor(ArticleAiAction.DIGEST))
        val id = sha256(
            listOf(
                "visible_articles",
                articleIDs.joinToString(","),
                settings.provider.name,
                settings.baseUrl,
                settings.model,
                settings.language,
                promptHash,
                contentHash,
            ).joinToString("|")
        )

        return ArticleAiDigestInput(
            id = id,
            filterJson = filterJson,
            provider = settings.provider.name,
            model = settings.model,
            language = settings.language,
            articleIdsJson = articleIdsJson,
        )
    }

    private fun String.escapeJson(): String {
        return buildString {
            this@escapeJson.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
        }
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private data class ArticleAiSettings(
        val enabled: Boolean,
        val provider: AiProvider,
        val baseUrl: String,
        val apiKey: String,
        val model: String,
        val language: String,
        val maxInputCharacters: Int,
        val translatePrompt: String,
        val summarizePrompt: String,
        val previewSummaryPrompt: String,
        val keyPointsPrompt: String,
    ) {
        fun promptFor(action: ArticleAiAction): String {
            return when (action) {
                ArticleAiAction.TRANSLATE -> translatePrompt
                ArticleAiAction.SUMMARIZE -> summarizePrompt
                ArticleAiAction.PREVIEW_SUMMARY -> previewSummaryPrompt
                ArticleAiAction.KEY_POINTS -> keyPointsPrompt
                ArticleAiAction.QUESTION -> ArticleAiPrompts.QUESTION
                ArticleAiAction.DIGEST -> ArticleAiPrompts.DIGEST
            }
        }

        companion object {
            fun from(appPreferences: AppPreferences): ArticleAiSettings {
                val provider = appPreferences.aiOptions.provider.get()

                return ArticleAiSettings(
                    enabled = appPreferences.aiOptions.enabled.get(),
                    provider = provider,
                    baseUrl = appPreferences.aiOptions.baseUrl.get().ifBlank { provider.defaultBaseUrl },
                    apiKey = appPreferences.aiOptions.apiKey.get().trim(),
                    model = appPreferences.aiOptions.model.get().ifBlank { provider.defaultModel },
                    language = appPreferences.aiOptions.language.get().ifBlank { "English" },
                    maxInputCharacters = appPreferences.aiOptions.maxInputCharacters.get()
                        .coerceAtLeast(MIN_INPUT_CHARACTERS),
                    translatePrompt = appPreferences.aiOptions.translatePrompt.get()
                        .ifBlank { ArticleAiPrompts.TRANSLATE },
                    summarizePrompt = appPreferences.aiOptions.summarizePrompt.get()
                        .ifBlank { ArticleAiPrompts.SUMMARIZE },
                    previewSummaryPrompt = appPreferences.aiOptions.previewSummaryPrompt.get()
                        .ifBlank { ArticleAiPrompts.PREVIEW_SUMMARY },
                    keyPointsPrompt = appPreferences.aiOptions.keyPointsPrompt.get()
                        .ifBlank { ArticleAiPrompts.KEY_POINTS },
                )
            }
        }
    }

    companion object {
        private const val MIN_INPUT_CHARACTERS = 1000
        private const val MAX_DIGEST_ARTICLES = 12
        private const val MAX_DIGEST_ARTICLE_CHARACTERS = 1800
        private const val MAX_SUMMARY_CHUNKS = 8
    }
}
