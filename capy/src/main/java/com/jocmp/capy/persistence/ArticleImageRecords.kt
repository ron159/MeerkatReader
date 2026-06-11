package com.jocmp.capy.persistence

import com.jocmp.capy.articles.ArticleImageExtractor
import com.jocmp.capy.articles.CachedArticleImage
import com.jocmp.capy.common.TimeHelpers.nowUTC
import com.jocmp.capy.common.optionalURL
import com.jocmp.capy.common.transactionWithErrorHandling
import com.jocmp.capy.common.withIOContext
import com.jocmp.capy.db.Database
import java.security.MessageDigest

class ArticleImageRecords(
    private val database: Database,
    private val extractor: ArticleImageExtractor = ArticleImageExtractor(),
) {
    fun replaceArticleRefs(
        articleID: String,
        contentHTML: String?,
        articleURL: String?,
        siteURL: String?,
    ) {
        if (articleID.isBlank()) {
            return
        }

        val refs = extractor.extract(
            html = contentHTML,
            articleUrl = optionalURL(articleURL),
            siteUrl = siteURL,
        )
        val now = nowUTC().toEpochSecond()

        database.transactionWithErrorHandling {
            database.articleImagesQueries.deleteRefsByArticleID(articleID)

            refs.forEach { ref ->
                val assetID = assetID(ref.resolvedUrl)

                database.articleImagesQueries.upsertAsset(
                    id = assetID,
                    sourceURL = ref.resolvedUrl,
                    status = Status.PENDING.value,
                    createdAt = now,
                    updatedAt = now,
                )
                database.articleImagesQueries.insertRef(
                    articleID = articleID,
                    assetID = assetID,
                    originalSrc = ref.originalSrc,
                    resolvedURL = ref.resolvedUrl,
                    ordinal = ref.ordinal.toLong(),
                    altText = ref.altText,
                    createdAt = now,
                )
            }
        }
    }

    suspend fun findRefs(articleID: String): List<ArticleImageRefRecord> = withIOContext {
        database.articleImagesQueries.findRefsByArticleID(
            articleID = articleID,
            mapper = { _, assetID, originalSrc, resolvedURL, ordinal, altText, _, status, relativePath, mimeType ->
                ArticleImageRefRecord(
                    assetID = assetID,
                    originalSrc = originalSrc,
                    resolvedURL = resolvedURL,
                    ordinal = ordinal.toInt(),
                    altText = altText,
                    status = status,
                    relativePath = relativePath,
                    mimeType = mimeType,
                )
            }
        ).executeAsList()
    }

    suspend fun findCachedImages(articleID: String): List<CachedArticleImage> = withIOContext {
        database.articleImagesQueries.findReadyRefsByArticleID(
            articleID = articleID,
            mapper = { _, assetID, originalSrc, resolvedURL, ordinal, altText, _, relativePath, mimeType ->
                CachedArticleImage(
                    assetID = assetID,
                    originalSrc = originalSrc,
                    resolvedURL = resolvedURL,
                    ordinal = ordinal.toInt(),
                    altText = altText,
                    relativePath = relativePath.orEmpty(),
                    mimeType = mimeType,
                )
            }
        ).executeAsList()
    }

    fun downloadCandidates(limit: Long = DEFAULT_DOWNLOAD_LIMIT): List<ArticleImageDownloadCandidate> {
        return database.articleImagesQueries.findDownloadCandidates(
            staleBefore = nowUTC().toEpochSecond() - DOWNLOADING_STALE_SECONDS,
            maxFailureCount = MAX_FAILURE_COUNT.toLong(),
            limit = limit,
            mapper = { id, sourceURL, articleURL ->
                ArticleImageDownloadCandidate(
                    assetID = id,
                    sourceURL = sourceURL,
                    articleURL = articleURL,
                )
            }
        ).executeAsList()
    }

    fun downloadCandidatesForArticle(
        articleID: String,
        limit: Long = DEFAULT_DOWNLOAD_LIMIT,
    ): List<ArticleImageDownloadCandidate> {
        return database.articleImagesQueries.findDownloadCandidatesByArticleID(
            articleID = articleID,
            staleBefore = nowUTC().toEpochSecond() - DOWNLOADING_STALE_SECONDS,
            maxFailureCount = MAX_FAILURE_COUNT.toLong(),
            limit = limit,
            mapper = { id, sourceURL, articleURL ->
                ArticleImageDownloadCandidate(
                    assetID = id,
                    sourceURL = sourceURL,
                    articleURL = articleURL,
                )
            }
        ).executeAsList()
    }

    fun assetsWithFiles(): List<ArticleImageStoredAsset> {
        return database.articleImagesQueries.findAssetsWithFiles(
            mapper = { assetID, relativePath, byteSize, lastAccessedAt ->
                storedAssetMapper(assetID, relativePath, byteSize, lastAccessedAt)
            }
        ).executeAsList()
    }

    fun readyAssetsWithFiles(): List<ArticleImageStoredAsset> {
        return database.articleImagesQueries.findReadyAssetsWithFiles(
            mapper = { assetID, relativePath, byteSize, lastAccessedAt ->
                storedAssetMapper(assetID, relativePath, byteSize, lastAccessedAt)
            }
        ).executeAsList()
    }

    fun markDownloading(assetID: String) {
        database.articleImagesQueries.markDownloading(
            id = assetID,
            updatedAt = nowUTC().toEpochSecond(),
        )
    }

    fun markReady(
        assetID: String,
        finalURL: String?,
        relativePath: String,
        mimeType: String?,
        byteSize: Long,
        etag: String?,
        lastModified: String?,
    ) {
        val now = nowUTC().toEpochSecond()

        database.articleImagesQueries.markReady(
            id = assetID,
            finalURL = finalURL,
            relativePath = relativePath,
            mimeType = mimeType,
            byteSize = byteSize,
            etag = etag,
            lastModified = lastModified,
            fetchedAt = now,
            updatedAt = now,
        )
    }

    fun markFailed(assetID: String, error: String?) {
        database.articleImagesQueries.markFailed(
            id = assetID,
            lastError = error?.take(MAX_ERROR_LENGTH),
            updatedAt = nowUTC().toEpochSecond(),
        )
    }

    fun markSkipped(assetID: String, reason: String?) {
        database.articleImagesQueries.markSkipped(
            id = assetID,
            lastError = reason?.take(MAX_ERROR_LENGTH),
            updatedAt = nowUTC().toEpochSecond(),
        )
    }

    fun markPending(assetID: String, reason: String?) {
        database.articleImagesQueries.markPending(
            id = assetID,
            lastError = reason?.take(MAX_ERROR_LENGTH),
            updatedAt = nowUTC().toEpochSecond(),
        )
    }

    fun markPruned(assetID: String, reason: String?) {
        database.articleImagesQueries.markPruned(
            id = assetID,
            lastError = reason?.take(MAX_ERROR_LENGTH),
            updatedAt = nowUTC().toEpochSecond(),
        )
    }

    fun resetStoredAssets(reason: String?) {
        database.articleImagesQueries.resetStoredAssets(
            lastError = reason?.take(MAX_ERROR_LENGTH),
            updatedAt = nowUTC().toEpochSecond(),
        )
    }

    fun resetDownloadingAssets(reason: String?) {
        database.articleImagesQueries.resetDownloadingAssets(
            lastError = reason?.take(MAX_ERROR_LENGTH),
            updatedAt = nowUTC().toEpochSecond(),
        )
    }

    fun touch(assetID: String) {
        database.articleImagesQueries.touchAsset(
            id = assetID,
            lastAccessedAt = nowUTC().toEpochSecond(),
        )
    }

    fun deleteRefs(articleIDs: List<String>) {
        if (articleIDs.isEmpty()) {
            return
        }

        database.articleImagesQueries.deleteRefsByArticleIDs(articleIDs)
    }

    fun deleteRefsWithoutArticle() {
        database.articleImagesQueries.deleteRefsWithoutArticle()
    }

    fun orphanedAssetIDs(): List<String> {
        return database.articleImagesQueries.findOrphanedAssetIDs().executeAsList()
    }

    fun orphanedAssets(): List<ArticleImageStoredAsset> {
        return database.articleImagesQueries.findOrphanedAssets(
            mapper = { assetID, relativePath, byteSize, lastAccessedAt ->
                storedAssetMapper(assetID, relativePath, byteSize, lastAccessedAt)
            }
        ).executeAsList()
    }

    fun deleteAssets(assetIDs: List<String>) {
        if (assetIDs.isEmpty()) {
            return
        }

        database.articleImagesQueries.deleteAssetsByID(assetIDs)
    }

    enum class Status(val value: String) {
        PENDING("pending"),
        DOWNLOADING("downloading"),
        READY("ready"),
        FAILED("failed"),
        SKIPPED("skipped"),
        PRUNED("pruned"),
    }

    private fun storedAssetMapper(
        assetID: String,
        relativePath: String?,
        byteSize: Long?,
        lastAccessedAt: Long?,
    ): ArticleImageStoredAsset {
        return ArticleImageStoredAsset(
            assetID = assetID,
            relativePath = relativePath,
            byteSize = byteSize,
            lastAccessedAt = lastAccessedAt,
        )
    }

    companion object {
        private const val DEFAULT_DOWNLOAD_LIMIT = 100L
        private const val DOWNLOADING_STALE_SECONDS = 2L * 60L * 60L
        private const val MAX_FAILURE_COUNT = 3
        private const val MAX_ERROR_LENGTH = 512

        fun assetID(sourceURL: String): String {
            val bytes = MessageDigest
                .getInstance("SHA-256")
                .digest(sourceURL.toByteArray(Charsets.UTF_8))

            return bytes.joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }
        }
    }
}

data class ArticleImageRefRecord(
    val assetID: String,
    val originalSrc: String,
    val resolvedURL: String,
    val ordinal: Int,
    val altText: String?,
    val status: String,
    val relativePath: String?,
    val mimeType: String?,
)

data class ArticleImageDownloadCandidate(
    val assetID: String,
    val sourceURL: String,
    val articleURL: String?,
)

data class ArticleImageStoredAsset(
    val assetID: String,
    val relativePath: String?,
    val byteSize: Long?,
    val lastAccessedAt: Long?,
)
