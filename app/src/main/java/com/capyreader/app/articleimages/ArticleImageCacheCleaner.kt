package com.capyreader.app.articleimages

import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.preferences.ArticleImageCacheCleanupInterval
import com.jocmp.capy.logging.CapyLog
import com.jocmp.capy.persistence.ArticleFullContentRecords
import com.jocmp.capy.persistence.ArticleImageRecords
import com.jocmp.capy.persistence.ArticleImageStoredAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

class ArticleImageCacheCleaner(
    private val records: ArticleImageRecords,
    private val store: ArticleImageStore,
    private val appPreferences: AppPreferences,
    private val fullContentRecords: ArticleFullContentRecords,
) {
    suspend fun cleanup(force: Boolean = false) = withContext(Dispatchers.IO) {
        reconcileFiles()
        deleteOrphanedAssets()

        val maxBytes = appPreferences.articleImageCacheSize.get().maxBytes ?: return@withContext
        if (force || shouldRunScheduledCleanup()) {
            pruneTo(maxBytes)
            appPreferences.lastArticleImageCacheCleanupAt.set(nowEpochSeconds())
        }
    }

    suspend fun cleanup(maxBytes: Long) = withContext(Dispatchers.IO) {
        reconcileFiles()
        deleteOrphanedAssets()
        pruneTo(maxBytes)
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        resetInFlightDownloads()
        store.deleteAll()
        records.resetStoredAssets("Cache cleared")
        deleteOrphanedAssets()
    }

    suspend fun resetInFlightDownloads() = withContext(Dispatchers.IO) {
        records.resetDownloadingAssets("Download cancelled")
    }

    private fun reconcileFiles() {
        val assets = records.assetsWithFiles()
        val knownPaths = assets
            .mapNotNull { it.relativePath }
            .toSet()

        store.files()
            .filterNot { it.relativePath in knownPaths }
            .forEach { store.delete(it.relativePath) }

        assets
            .filterNot { it.fileExists() }
            .forEach { records.markPending(it.assetID, "Cached file missing") }
    }

    private suspend fun deleteOrphanedAssets() {
        fullContentRecords.deleteOrphans()
        records.deleteRefsWithoutArticle()

        val orphanedAssets = records.orphanedAssets()

        orphanedAssets.forEach { asset ->
            store.delete(asset.relativePath)
        }

        records.deleteAssets(orphanedAssets.map { it.assetID })
    }

    private fun pruneTo(maxBytes: Long) {
        val limit = maxBytes.coerceAtLeast(0L)
        val cachedAssets = records
            .readyAssetsWithFiles()
            .mapNotNull { asset ->
                val byteSize = asset.localByteSize() ?: return@mapNotNull null
                CachedAsset(asset = asset, byteSize = byteSize)
            }

        var totalBytes = cachedAssets.sumOf { it.byteSize }
        if (totalBytes <= limit) {
            return
        }

        for (cached in cachedAssets) {
            if (totalBytes <= limit) {
                return
            }

            if (store.delete(cached.asset.relativePath)) {
                totalBytes -= cached.byteSize
                records.markPruned(cached.asset.assetID, "Cache pruned")
            } else {
                CapyLog.warn(
                    "article_image_prune",
                    mapOf(
                        "asset_id" to cached.asset.assetID,
                        "relative_path" to cached.asset.relativePath,
                    )
                )
            }
        }
    }

    private fun shouldRunScheduledCleanup(): Boolean {
        val interval = appPreferences.articleImageCacheCleanupInterval.get()
        if (interval == ArticleImageCacheCleanupInterval.MANUAL) {
            return false
        }

        val intervalSeconds = interval.intervalSeconds ?: return false
        val lastCleanupAt = appPreferences.lastArticleImageCacheCleanupAt.get()

        return lastCleanupAt == 0L ||
            nowEpochSeconds() - lastCleanupAt >= intervalSeconds
    }

    private fun nowEpochSeconds(): Long = Instant.now().epochSecond

    private fun ArticleImageStoredAsset.fileExists(): Boolean {
        val relativePath = relativePath ?: return false
        val file = store.fileForRelativePath(relativePath) ?: return false

        return file.exists() && file.isFile
    }

    private fun ArticleImageStoredAsset.localByteSize(): Long? {
        val relativePath = relativePath ?: return null
        val file = store.fileForRelativePath(relativePath) ?: return null

        return if (file.exists() && file.isFile) {
            file.length()
        } else {
            records.markPending(assetID, "Cached file missing")
            null
        }
    }

    private data class CachedAsset(
        val asset: ArticleImageStoredAsset,
        val byteSize: Long,
    )

    companion object {
        const val MAX_CACHE_BYTES = 1024L * 1024L * 1024L
    }
}
