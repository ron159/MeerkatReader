package com.jocmp.capy

interface ArticleExportIntegration {
    val id: String
    val displayName: String

    suspend fun save(article: Article): Result<Unit>
}
