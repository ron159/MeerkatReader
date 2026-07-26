package com.capyreader.app.ai

import com.capyreader.app.preferences.AiTranslationMode
import com.jocmp.capy.Article
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.ZonedDateTime

class ArticleAiContentTest {
    @Test
    fun `loading AI card exposes a polite atomic status`() {
        val document = Jsoup.parseBodyFragment(
            renderedTopContent(
                ArticleAiDisplayState(
                    action = ArticleAiAction.SUMMARIZE,
                    isLoading = true,
                )
            )
        )
        val card = document.selectFirst(".ai-card--loading")

        assertEquals("status", card?.attr("role"))
        assertEquals("polite", card?.attr("aria-live"))
        assertEquals("true", card?.attr("aria-atomic"))
        assertEquals("Summary. Working", card?.attr("aria-label"))
        assertEquals("true", card?.selectFirst(".ai-card__eyebrow")?.attr("aria-hidden"))
        assertEquals("true", card?.selectFirst(".ai-shimmer")?.parent()?.attr("aria-hidden"))
    }

    @Test
    fun `failed AI card exposes a polite status without raw decoration`() {
        val document = Jsoup.parseBodyFragment(
            renderedTopContent(
                ArticleAiDisplayState(
                    action = ArticleAiAction.KEY_POINTS,
                    error = "Provider unavailable",
                )
            )
        )
        val card = document.selectFirst(".ai-card--error")

        assertEquals("status", card?.attr("role"))
        assertEquals("polite", card?.attr("aria-live"))
        assertEquals("true", card?.attr("aria-atomic"))
        assertEquals("Key points. Provider unavailable", card?.attr("aria-label"))
        assertEquals("Provider unavailable", card?.selectFirst(".ai-card__content")?.text())
    }

    @Test
    fun `successful AI card is a labelled region with accessible copy targets`() {
        val document = Jsoup.parseBodyFragment(
            renderedTopContent(
                ArticleAiDisplayState(
                    action = ArticleAiAction.SUMMARIZE,
                    result = "Generated summary",
                )
            )
        )
        val card = document.selectFirst(".ai-card")
        val paragraph = card?.selectFirst(".ai-card__content > p")

        assertEquals("region", card?.attr("role"))
        assertEquals("Summary", card?.attr("aria-label"))
        assertEquals("button", paragraph?.attr("role"))
        assertEquals("0", paragraph?.attr("tabindex"))
        assertEquals("dialog", paragraph?.attr("aria-haspopup"))
        assertEquals("Generated summary. Copy text", paragraph?.attr("aria-label"))
    }

    @Test
    fun `replace translation preserves code and table structure`() {
        val document = Jsoup.parseBodyFragment(renderedContent(AiTranslationMode.REPLACE_ORIGINAL))

        assertEquals(
            listOf("Translated introduction", "Translated ending"),
            document.select("body > p:not(:has(code))").map { it.text() },
        )
        assertEquals("Run sync() now.", document.selectFirst("p:has(code)")?.text())
        assertEquals("sync()", document.selectFirst("p > code")?.text())
        assertEquals("val answer = 42", document.selectFirst("pre > code")?.text())
        assertEquals("Measurements", document.selectFirst("table > caption")?.text())
        assertEquals("Name", document.selectFirst("table th[scope=col]")?.text())
        assertEquals("forty-two", document.selectFirst("table td")?.text())
    }

    @Test
    fun `parallel translation preserves code and table structure`() {
        val document = Jsoup.parseBodyFragment(renderedContent(AiTranslationMode.PARALLEL))

        assertEquals(2, document.select(".ai-translation-row").size)
        assertEquals("Run sync() now.", document.selectFirst("p:has(code)")?.text())
        assertEquals("sync()", document.selectFirst("p > code")?.text())
        assertEquals("val answer = 42", document.selectFirst("pre > code")?.text())
        assertEquals("Measurements", document.selectFirst("table > caption")?.text())
        assertNotNull(document.selectFirst("table thead"))
        assertNotNull(document.selectFirst("table tbody"))
        assertEquals("forty-two", document.selectFirst("table td")?.text())
        assertEquals("region", document.selectFirst(".ai-translation")?.attr("role"))
        assertEquals("Translation", document.selectFirst(".ai-translation")?.attr("aria-label"))
        assertEquals(
            "Introduction. Copy text",
            document.selectFirst(".ai-translation-row__original")?.attr("aria-label"),
        )
        assertEquals(
            "Translated introduction. Copy text",
            document.selectFirst(".ai-translation-row__translated")?.attr("aria-label"),
        )
    }

    private fun renderedContent(mode: AiTranslationMode): String {
        return article().withAiDisplayContent(
            topState = null,
            translationState = ArticleAiDisplayState(
                action = ArticleAiAction.TRANSLATE,
                result = "Translated introduction\n\nTranslated ending",
            ),
            translationMode = mode,
            labels = labels(),
        ).content
    }

    private fun renderedTopContent(state: ArticleAiDisplayState): String {
        return article().withAiDisplayContent(
            topState = state,
            translationState = null,
            translationMode = AiTranslationMode.REPLACE_ORIGINAL,
            labels = labels(),
        ).content
    }

    private fun labels() = ArticleAiLabels(
        translation = "Translation",
        summary = "Summary",
        previewSummary = "Preview",
        keyPoints = "Key points",
        answer = "Answer",
        digest = "Digest",
        workingOnIt = "Working",
        copyText = "Copy text",
    )

    private fun article(): Article {
        val content = """
            <p>Introduction</p>
            <p>Run <code>sync()</code> now.</p>
            <pre><code>val answer = 42</code></pre>
            <table>
              <caption>Measurements</caption>
              <thead>
                <tr><th scope="col">Name</th></tr>
              </thead>
              <tbody>
                <tr><td>forty-two</td></tr>
              </tbody>
            </table>
            <p>Ending</p>
        """.trimIndent()
        val now = ZonedDateTime.parse("2026-07-25T00:00:00Z")

        return Article(
            id = "article",
            feedID = "feed",
            title = "Structured article",
            author = null,
            contentHTML = content,
            url = null,
            summary = "",
            imageURL = null,
            updatedAt = now,
            publishedAt = now,
            read = false,
            starred = false,
        )
    }
}
