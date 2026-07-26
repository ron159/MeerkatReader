package com.capyreader.app.integrations.wallabag

import com.jocmp.capy.Article
import com.jocmp.capy.ArticleExportIntegration
import com.jocmp.capy.ArticleExportResult

class WallabagIntegration(
    private val client: WallabagClient,
) : ArticleExportIntegration {
    override val id = ID
    override val displayName = "Wallabag"

    override suspend fun save(article: Article): Result<ArticleExportResult> {
        return client.save(article)
    }

    companion object {
        const val ID = "wallabag"
    }
}
