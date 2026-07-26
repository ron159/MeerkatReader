package com.capyreader.app.ai

import android.text.Html
import com.capyreader.app.preferences.AiTranslationMode
import com.jocmp.capy.Article
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

data class ArticleAiDisplayState(
    val action: ArticleAiAction,
    val isLoading: Boolean = false,
    val result: String? = null,
    val error: String? = null,
)

data class ArticleAiLabels(
    val translation: String,
    val summary: String,
    val previewSummary: String,
    val keyPoints: String,
    val answer: String,
    val digest: String,
    val workingOnIt: String,
    val copyText: String,
) {
    fun labelFor(action: ArticleAiAction): String = when (action) {
        ArticleAiAction.TRANSLATE -> translation
        ArticleAiAction.SUMMARIZE -> summary
        ArticleAiAction.PREVIEW_SUMMARY -> previewSummary
        ArticleAiAction.KEY_POINTS -> keyPoints
        ArticleAiAction.QUESTION -> answer
        ArticleAiAction.DIGEST -> digest
    }
}

fun Article.withAiDisplayContent(
    topState: ArticleAiDisplayState?,
    translationState: ArticleAiDisplayState?,
    translationMode: AiTranslationMode,
    labels: ArticleAiLabels,
): Article {
    val originalContent = content.ifBlank { defaultContent }
    val topHtml = topState?.toTopCardHtml(labels).orEmpty()
    val bodyHtml = when {
        translationState?.isLoading == true -> originalContent
        translationState?.error != null -> translationState.toErrorCardHtml(labels) + originalContent
        translationState?.result != null -> when (translationMode) {
            AiTranslationMode.REPLACE_ORIGINAL -> translatedHtmlPreservingStructure(
                originalContent,
                translationState.result,
            )
            AiTranslationMode.PARALLEL -> parallelTranslationHtml(originalContent, translationState.result, labels)
        }
        else -> originalContent
    }

    return copy(content = topHtml + bodyHtml)
}

fun Article.plainTextContent(): String {
    val html = content.ifBlank { defaultContent }
    return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
        .toString()
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

private fun ArticleAiDisplayState.toTopCardHtml(labels: ArticleAiLabels): String {
    val label = labels.labelFor(action).escapeHtml()

    return when {
        isLoading -> {
            val statusLabel = "${labels.labelFor(action)}. ${labels.workingOnIt}".escapeHtml()
            """
            <section class="ai-card ai-card--loading" data-capy-ai-preserve="true"
              role="status" aria-live="polite" aria-atomic="true" aria-label="$statusLabel">
              <div class="ai-card__eyebrow" aria-hidden="true">$label</div>
              <div class="ai-card__title">${labels.workingOnIt.escapeHtml()}</div>
              <div aria-hidden="true">
                <div class="ai-shimmer ai-shimmer--wide"></div>
                <div class="ai-shimmer"></div>
                <div class="ai-shimmer ai-shimmer--short"></div>
              </div>
            </section>
            """.trimIndent()
        }
        error != null -> toErrorCardHtml(labels)
        result != null -> """
            <section class="ai-card" data-capy-ai-preserve="true"
              role="region" aria-label="$label">
              <div class="ai-card__eyebrow" aria-hidden="true">$label</div>
              <div class="ai-card__content">${result.toAccessibleArticleHtml(labels)}</div>
            </section>
        """.trimIndent()
        else -> ""
    }
}

private fun ArticleAiDisplayState.toErrorCardHtml(labels: ArticleAiLabels): String {
    val label = labels.labelFor(action).escapeHtml()
    val statusLabel = "${labels.labelFor(action)}. ${error.orEmpty()}".escapeHtml()

    return """
        <section class="ai-card ai-card--error" data-capy-ai-preserve="true"
          role="status" aria-live="polite" aria-atomic="true" aria-label="$statusLabel">
          <div class="ai-card__eyebrow" aria-hidden="true">$label</div>
          <div class="ai-card__content">${error.orEmpty().escapeHtml()}</div>
        </section>
    """.trimIndent()
}

private fun parallelTranslationHtml(
    originalHtml: String,
    translatedText: String,
    labels: ArticleAiLabels,
): String {
    val document = Jsoup.parseBodyFragment(originalHtml)
    val translatedBlocks = translatedText.toPlainBlocks().toMutableList()
    val rows = document.body().children().joinToString("") { element ->
        parallelElementHtml(element, translatedBlocks, labels)
    }
    val label = labels.translation.escapeHtml()

    return """
        <section class="ai-translation" data-capy-ai-preserve="true"
          role="region" aria-label="$label">
          <div class="ai-card__eyebrow" aria-hidden="true">$label</div>
          $rows
        </section>
    """.trimIndent()
}

private fun parallelElementHtml(
    element: Element,
    translatedBlocks: MutableList<String>,
    labels: ArticleAiLabels,
): String {
    if (element.shouldPreserveWholeElement()) {
        return element.outerHtml()
    }

    if (element.isTextBlock() && !element.hasPreservedDescendant()) {
        return parallelRowHtml(
            original = element.text(),
            translated = translatedBlocks.removeFirstOrNull().orEmpty(),
            labels = labels,
        )
    }

    val children = element.children().joinToString("") { child ->
        parallelElementHtml(child, translatedBlocks, labels)
    }
    val ownText = element.ownText()

    return if (ownText.isBlank()) {
        children
    } else {
        parallelRowHtml(
            original = ownText,
            translated = translatedBlocks.removeFirstOrNull().orEmpty(),
            labels = labels,
        ) + children
    }
}

private fun parallelRowHtml(
    original: String,
    translated: String,
    labels: ArticleAiLabels,
): String {
    val originalLabel = copyAccessibilityLabel(original, labels.copyText)
    val translatedLabel = copyAccessibilityLabel(translated, labels.copyText)

    return """
        <div class="ai-translation-row">
          <div class="ai-translation-row__original" role="button" tabindex="0"
            aria-haspopup="dialog" aria-label="$originalLabel">${original.escapeHtml()}</div>
          <div class="ai-translation-row__translated" role="button" tabindex="0"
            aria-haspopup="dialog" aria-label="$translatedLabel">${translated.escapeHtml()}</div>
        </div>
    """.trimIndent()
}

private fun String.toAccessibleArticleHtml(labels: ArticleAiLabels): String {
    val document = Jsoup.parseBodyFragment(toArticleHtml())

    document.body().children()
        .filter { it.tagName() == "p" || it.tagName() == "ul" }
        .forEach { element ->
            element.attr("role", "button")
            element.attr("tabindex", "0")
            element.attr("aria-haspopup", "dialog")
            element.attr(
                "aria-label",
                copyAccessibilityLabel(element.text(), labels.copyText),
            )
        }

    return document.body().html()
}

private fun copyAccessibilityLabel(text: String, copyText: String): String {
    return "$text. $copyText".escapeHtml()
}

private fun translatedHtmlPreservingStructure(
    originalHtml: String,
    translatedText: String,
): String {
    val document = Jsoup.parseBodyFragment(originalHtml)
    val translatedBlocks = translatedText.toPlainBlocks().toMutableList()
    val clearUntranslatedText = translatedBlocks.size < countTextBlocks(document.body())

    document.body().children().forEach { element ->
        translateTextIn(element, translatedBlocks, clearUntranslatedText)
    }

    return document.body().html()
}

private fun translateTextIn(
    element: Element,
    translatedBlocks: MutableList<String>,
    clearUntranslatedText: Boolean,
) {
    if (element.shouldPreserveWholeElement()) {
        return
    }

    if (element.isTextBlock() && !element.hasPreservedDescendant()) {
        val replacement = translatedBlocks.removeFirstOrNull()
        if (replacement == null) {
            if (clearUntranslatedText) {
                element.text("")
            }
            return
        }
        element.text(replacement)
        return
    }

    element.children().forEach { child ->
        translateTextIn(child, translatedBlocks, clearUntranslatedText)
    }

    val textNodes = element.textNodes().filter { it.text().isNotBlank() }
    if (textNodes.isEmpty()) {
        return
    }

    val replacement = translatedBlocks.removeFirstOrNull()
    if (replacement == null) {
        if (clearUntranslatedText) {
            textNodes.forEach { it.text("") }
        }
        return
    }
    textNodes.first().text(replacement)
    textNodes.drop(1).forEach { it.text("") }
}

private fun countTextBlocks(element: Element): Int {
    if (element.shouldPreserveWholeElement()) {
        return 0
    }

    if (element.isTextBlock() && !element.hasPreservedDescendant() && element.text().isNotBlank()) {
        return 1
    }

    val childCount = element.children().sumOf(::countTextBlocks)
    val ownTextCount = if (element.ownText().isNotBlank()) 1 else 0
    return childCount + ownTextCount
}

private fun Element.isTextBlock(): Boolean {
    return tagName() in setOf(
        "p",
        "li",
        "blockquote",
        "figcaption",
        "h1",
        "h2",
        "h3",
        "h4",
        "h5",
        "h6",
    )
}

private fun Element.hasPreservedDescendant(): Boolean {
    return allElements.any { it != this && it.isPreservedElement() }
}

private fun Element.shouldPreserveWholeElement(): Boolean {
    return isPreservedElement() ||
        (isTextBlock() && allElements.any { it != this && it.tagName() in STRUCTURED_TAGS })
}

private fun Element.isPreservedElement(): Boolean {
    return isMediaElement() || tagName() in STRUCTURED_TAGS
}

private fun Element.isMediaElement(): Boolean {
    return tagName() in setOf(
        "img",
        "picture",
        "source",
        "video",
        "audio",
        "iframe",
        "object",
        "embed",
        "svg",
        "canvas",
    )
}

private val STRUCTURED_TAGS = setOf(
    "pre",
    "code",
    "table",
)

private fun String.toArticleHtml(): String {
    val blocks = toPlainBlocks()
    if (blocks.isEmpty()) {
        return ""
    }

    return blocks.joinToString("") { block ->
        val lines = block.lines().filter { it.isNotBlank() }
        if (lines.isNotEmpty() && lines.all { it.trimStart().startsWith("- ") || it.trimStart().startsWith("* ") }) {
            lines.joinToString(prefix = "<ul>", postfix = "</ul>", separator = "") {
                "<li>${it.trimStart().drop(2).escapeHtml()}</li>"
            }
        } else {
            "<p>${block.escapeHtml().replace("\n", "<br>")}</p>"
        }
    }
}

private fun String.toPlainBlocks(): List<String> {
    return trim()
        .replace("\r\n", "\n")
        .split(Regex("\\n\\s*\\n"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
}

private fun String.escapeHtml(): String {
    return replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
