package com.capyreader.app.offline

import com.capyreader.app.articleimages.ArticleImageDownloader
import com.capyreader.app.preferences.AppPreferences
import com.jocmp.capy.Account
import com.jocmp.capy.Article
import com.jocmp.capy.ArticleOfflinePackageState
import com.jocmp.capy.FeedOfflinePolicy
import com.jocmp.capy.common.withIOContext
import com.jocmp.capy.latestArticles
import com.jocmp.capy.logging.CapyLog
import com.jocmp.capy.persistence.ArticleFullContentRecords
import com.jocmp.capy.persistence.ArticleImageRecords
import com.jocmp.capy.persistence.ArticleOfflinePackageInput
import com.jocmp.capy.persistence.ArticleOfflinePackageRecord
import com.jocmp.capy.persistence.ArticleOfflinePackageRecords
import com.jocmp.capy.persistence.ArticleReadingProgressRecords
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class ArticleOfflinePackageDownloader(
    private val account: Account,
    private val packageRecords: ArticleOfflinePackageRecords,
    private val fullContentRecords: ArticleFullContentRecords,
    private val imageRecords: ArticleImageRecords,
    private val readingProgressRecords: ArticleReadingProgressRecords,
    private val imageDownloader: ArticleImageDownloader,
    private val appPreferences: AppPreferences,
) {
    suspend fun queue(
        articleID: String,
        includeFullContent: Boolean = true,
        includeImages: Boolean = true,
        includeAudio: Boolean = false,
    ) {
        packageRecords.upsert(
            ArticleOfflinePackageInput(
                articleID = articleID,
                state = ArticleOfflinePackageState.QUEUED,
                includeFullContent = includeFullContent,
                includeImages = includeImages,
                includeAudio = includeAudio,
            )
        )
    }

    suspend fun remove(articleID: String) {
        packageRecords.delete(articleID)
    }

    suspend fun findStates(articleIDs: Collection<String>): Map<String, ArticleOfflinePackageState> = withIOContext {
        articleIDs
            .distinct()
            .mapNotNull { articleID ->
                packageRecords.find(articleID)?.let { record ->
                    articleID to record.state
                }
            }
            .toMap()
    }

    suspend fun queueAlwaysKeepOffline(limit: Long = DEFAULT_AUTO_QUEUE_LIMIT): Int = withIOContext {
        val offlineOptions = appPreferences.offlineOptions
        if (!offlineOptions.enabled.get()) {
            return@withIOContext 0
        }

        var queued = 0

        account.latestArticles(limit = limit)
            .first()
            .forEach { article ->
                val feed = account.findFeed(article.feedID)
                if (feed?.offlinePolicy != FeedOfflinePolicy.ALWAYS) {
                    return@forEach
                }

                val existing = packageRecords.find(article.id)
                if (existing?.state in SKIP_AUTO_QUEUE_STATES) {
                    return@forEach
                }

                queue(
                    articleID = article.id,
                    includeFullContent = offlineOptions.includeFullContent.get(),
                    includeImages = offlineOptions.includeImages.get(),
                    includeAudio = offlineOptions.includeAudio.get() && article.enclosures.isNotEmpty(),
                )
                queued += 1
            }

        queued
    }

    suspend fun downloadQueued(limit: Int = DEFAULT_LIMIT): DownloadResult = withIOContext {
        val packages = (
            packageRecords.findByState(ArticleOfflinePackageState.QUEUED) +
                packageRecords.findByState(ArticleOfflinePackageState.STALE)
            ).take(limit)

        var failed = 0

        packages.forEach { offlinePackage ->
            try {
                if (!download(offlinePackage)) {
                    failed += 1
                }
            } catch (e: CancellationException) {
                packageRecords.updateState(
                    articleID = offlinePackage.articleID,
                    state = ArticleOfflinePackageState.QUEUED,
                    errorMessage = "Download cancelled",
                )
                throw e
            } catch (e: Exception) {
                failed += 1
                packageRecords.updateState(
                    articleID = offlinePackage.articleID,
                    state = ArticleOfflinePackageState.FAILED,
                    errorMessage = e.message,
                )
                CapyLog.warn(
                    "article_offline_package",
                    mapOf(
                        "article_id" to offlinePackage.articleID,
                        "error_type" to e::class.simpleName,
                        "error_message" to e.message,
                    )
                )
            }
        }

        DownloadResult(processed = packages.size, failed = failed)
    }

    suspend fun cleanup(): CleanupResult = withIOContext {
        val offlineOptions = appPreferences.offlineOptions
        packageRecords.deleteOrphans()
        val removedFailed = packageRecords.deleteByState(ArticleOfflinePackageState.FAILED)
        val protectedArticleIDs = protectedReadyPackageIDs()
        val prunedReady = packageRecords.pruneReadyPackages(
            maxPackages = DEFAULT_READY_PACKAGE_LIMIT,
            maxBytes = offlineOptions.storageLimitMegabytes.get().toLong() * BYTES_PER_MEGABYTE,
            preservedArticleIDs = protectedArticleIDs,
        )

        CleanupResult(
            removedFailed = removedFailed,
            prunedReady = prunedReady,
        )
    }

    private suspend fun protectedReadyPackageIDs(): Set<String> {
        val offlineOptions = appPreferences.offlineOptions

        if (!offlineOptions.preserveStarred.get() &&
            !offlineOptions.preserveSavedForLater.get() &&
            !offlineOptions.preserveRecentlyOpened.get() &&
            !offlineOptions.preserveFeedOffline.get()
        ) {
            return emptySet()
        }

        return packageRecords.findByState(ArticleOfflinePackageState.READY)
            .mapNotNull { offlinePackage ->
                val article = account.findArticle(offlinePackage.articleID) ?: return@mapNotNull null
                val feed = account.findFeed(article.feedID)
                val shouldProtect =
                    (offlineOptions.preserveStarred.get() && article.starred) ||
                        (offlineOptions.preserveSavedForLater.get() && article.isReadLater) ||
                        (offlineOptions.preserveRecentlyOpened.get() &&
                            readingProgressRecords.find(article.id) != null) ||
                        (offlineOptions.preserveFeedOffline.get() &&
                            feed?.offlinePolicy == FeedOfflinePolicy.ALWAYS)

                if (shouldProtect) article.id else null
            }
            .toSet()
    }

    private suspend fun download(offlinePackage: ArticleOfflinePackageRecord): Boolean {
        packageRecords.updateState(
            articleID = offlinePackage.articleID,
            state = ArticleOfflinePackageState.DOWNLOADING,
        )

        val article = account.findArticle(offlinePackage.articleID)
        if (article == null) {
            packageRecords.updateState(
                articleID = offlinePackage.articleID,
                state = ArticleOfflinePackageState.FAILED,
                errorMessage = "Article not found",
            )
            return false
        }

        val feed = account.findFeed(article.feedID)
        if (feed?.offlinePolicy == FeedOfflinePolicy.NEVER) {
            packageRecords.delete(article.id)
            return true
        }

        val content = contentFor(article, offlinePackage) ?: return false
        var bytes = content.toByteArray().size.toLong()

        if (offlinePackage.includeImages) {
            imageRecords.replaceArticleRefs(
                articleID = article.id,
                contentHTML = content,
                articleURL = article.url?.toString(),
                siteURL = article.siteURL,
            )
            imageDownloader.downloadPendingForArticle(article.id)
            bytes += imageRecords.readyBytesForArticle(article.id)
        }

        packageRecords.updateState(
            articleID = article.id,
            state = ArticleOfflinePackageState.READY,
            bytes = bytes,
        )

        return true
    }

    private suspend fun contentFor(
        article: Article,
        offlinePackage: ArticleOfflinePackageRecord,
    ): String? {
        if (!offlinePackage.includeFullContent) {
            return article.defaultContent
        }

        val cached = fullContentRecords.find(article.id)?.contentHTML
        if (!cached.isNullOrBlank()) {
            return cached
        }

        return account.fetchFullContent(article).fold(
            onSuccess = { content ->
                fullContentRecords.upsert(articleID = article.id, contentHTML = content)
                content
            },
            onFailure = { error ->
                packageRecords.updateState(
                    articleID = article.id,
                    state = ArticleOfflinePackageState.FAILED,
                    bytes = article.defaultContent.toByteArray().size.toLong(),
                    errorMessage = error.message,
                )
                CapyLog.warn(
                    "article_offline_full_content",
                    mapOf(
                        "article_id" to article.id,
                        "error_type" to error::class.simpleName,
                        "error_message" to error.message,
                    )
                )
                null
            },
        )
    }

    data class DownloadResult(
        val processed: Int,
        val failed: Int,
    ) {
        val shouldRetry: Boolean
            get() = failed > 0
    }

    data class CleanupResult(
        val removedFailed: Int,
        val prunedReady: Int,
    )

    companion object {
        private const val DEFAULT_LIMIT = 50
        private const val DEFAULT_AUTO_QUEUE_LIMIT = 100L
        private const val DEFAULT_READY_PACKAGE_LIMIT = 500
        private const val BYTES_PER_MEGABYTE = 1024L * 1024L
        private val SKIP_AUTO_QUEUE_STATES = setOf(
            ArticleOfflinePackageState.QUEUED,
            ArticleOfflinePackageState.DOWNLOADING,
            ArticleOfflinePackageState.READY,
        )
    }
}
