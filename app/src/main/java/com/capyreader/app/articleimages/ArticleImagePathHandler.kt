package com.capyreader.app.articleimages

import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import java.io.FileInputStream

class ArticleImagePathHandler(
    private val store: ArticleImageStore,
) : WebViewAssetLoader.PathHandler {
    override fun handle(path: String): WebResourceResponse? {
        val file = store.fileForRelativePath(path) ?: return null
        if (!file.exists() || !file.isFile) {
            return null
        }

        return WebResourceResponse(
            mimeType(path),
            null,
            FileInputStream(file),
        )
    }

    private fun mimeType(path: String): String {
        return when (path.substringAfterLast(".", "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "avif" -> "image/avif"
            "svg" -> "image/svg+xml"
            else -> "application/octet-stream"
        }
    }
}
