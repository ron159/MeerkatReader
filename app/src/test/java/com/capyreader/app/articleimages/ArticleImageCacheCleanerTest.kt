package com.capyreader.app.articleimages

import com.capyreader.app.preferences.AppPreferences
import com.jocmp.capy.persistence.ArticleFullContentRecords
import com.jocmp.capy.persistence.ArticleImageRecords
import com.jocmp.capy.persistence.ArticleImageStoredAsset
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File

class ArticleImageCacheCleanerTest {
    private val records = mockk<ArticleImageRecords>(relaxed = true)
    private val store = mockk<ArticleImageStore>(relaxed = true)
    private val appPreferences = mockk<AppPreferences>(relaxed = true)
    private val fullContentRecords = mockk<ArticleFullContentRecords>(relaxed = true)
    private val cleaner = ArticleImageCacheCleaner(records, store, appPreferences, fullContentRecords)

    @Test
    fun cleanupReconcilesFilesAndDeletesOrphanedAssets() = runTest {
        every { records.assetsWithFiles() } returns listOf(
            asset(assetID = "known", relativePath = "aa/known.jpg"),
            asset(assetID = "missing", relativePath = "aa/missing.jpg"),
        )
        every { store.files() } returns listOf(
            ArticleImageFile(relativePath = "aa/known.jpg", byteSize = 80),
            ArticleImageFile(relativePath = "aa/unknown.jpg", byteSize = 80),
        )
        every { store.fileForRelativePath("aa/known.jpg") } returns imageFile(byteSize = 80)
        every { store.fileForRelativePath("aa/missing.jpg") } returns null
        every { records.orphanedAssets() } returns listOf(
            asset(assetID = "orphan", relativePath = "aa/orphan.jpg")
        )
        every { records.readyAssetsWithFiles() } returns emptyList()

        cleaner.cleanup(maxBytes = 250)

        verify { store.delete("aa/unknown.jpg") }
        verify { records.markPending("missing", "Cached file missing") }
        verify { store.delete("aa/orphan.jpg") }
        verify { records.deleteAssets(listOf("orphan")) }
    }

    @Test
    fun cleanupPrunesOldestReadyAssetsOverLimit() = runTest {
        every { records.assetsWithFiles() } returns emptyList()
        every { store.files() } returns emptyList()
        every { records.orphanedAssets() } returns emptyList()
        every { records.readyAssetsWithFiles() } returns listOf(
            asset(assetID = "old", relativePath = "aa/old.jpg"),
            asset(assetID = "new", relativePath = "aa/new.jpg"),
        )
        every { store.fileForRelativePath("aa/old.jpg") } returns imageFile(byteSize = 80)
        every { store.fileForRelativePath("aa/new.jpg") } returns imageFile(byteSize = 80)
        every { store.delete("aa/old.jpg") } returns true

        cleaner.cleanup(maxBytes = 100)

        verify { store.delete("aa/old.jpg") }
        verify { records.markPruned("old", "Cache pruned") }
        verify(exactly = 0) { store.delete("aa/new.jpg") }
    }

    private fun asset(
        assetID: String,
        relativePath: String?,
        byteSize: Long? = null,
        lastAccessedAt: Long? = null,
    ): ArticleImageStoredAsset {
        return ArticleImageStoredAsset(
            assetID = assetID,
            relativePath = relativePath,
            byteSize = byteSize,
            lastAccessedAt = lastAccessedAt,
        )
    }

    private fun imageFile(byteSize: Long): File {
        return mockk {
            every { exists() } returns true
            every { isFile } returns true
            every { length() } returns byteSize
        }
    }
}
