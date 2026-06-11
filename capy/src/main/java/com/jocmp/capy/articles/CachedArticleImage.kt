package com.jocmp.capy.articles

data class CachedArticleImage(
    val assetID: String,
    val originalSrc: String,
    val resolvedURL: String,
    val ordinal: Int,
    val altText: String?,
    val relativePath: String,
    val mimeType: String?,
) {
    val localURL: String
        get() = "https://appassets.androidplatform.net/article-images/${relativePath.trimStart('/')}"
}
