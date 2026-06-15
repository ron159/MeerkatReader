package com.jocmp.capy.articles

import com.jocmp.capy.Article
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jsoup.Jsoup

fun parseHtml(
    article: Article,
    hideImages: Boolean
): String {
    val parsedContent = fullContentParserInput(article.content)
    val json = buildJsonObject {
        put("url", article.url?.toString())
        put("html", parsedContent.html)
        put("hideImages", hideImages)
    }

    return """
      ${parsedContent.preservedHtml}
      <script>
        (async () => {
          const input = ${scriptSafeJson(json)};
          displayFullContent(input);
        })();
      </script>
    """.trimIndent()
}

private fun fullContentParserInput(html: String): FullContentParserInput {
    if (!html.contains("data-capy-ai-preserve")) {
        return FullContentParserInput(
            html = html,
            preservedHtml = "",
        )
    }

    val document = Jsoup.parseBodyFragment(html)
    val preserved = document.select("[data-capy-ai-preserve]")
    val preservedHtml = preserved.joinToString(separator = "\n") { it.outerHtml() }

    preserved.remove()

    return FullContentParserInput(
        html = document.body().html(),
        preservedHtml = preservedHtml,
    )
}

private data class FullContentParserInput(
    val html: String,
    val preservedHtml: String,
)

private fun scriptSafeJson(json: JsonObject): String {
    return json.toString().replace("</", "<\\/")
}

fun postProcessScript(article: Article, hideImages: Boolean): String {
    val baseUrl = article.url?.toString() ?: article.siteURL ?: ""

    return """
      <script>
        (function() {
          postProcessContent("$baseUrl", $hideImages);
        })();
      </script>
    """.trimIndent()
}
