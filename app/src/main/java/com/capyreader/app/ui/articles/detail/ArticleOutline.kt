package com.capyreader.app.ui.articles.detail

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class ArticleHeadingCandidate(
    val domIndex: Int,
    val level: Int,
    val title: String,
    val id: String? = null,
)

data class ArticleOutlineItem(
    val domIndex: Int,
    val level: Int,
    val title: String,
    val targetID: String,
)

@Serializable
private data class ArticleHeadingTarget(
    val domIndex: Int,
    val targetID: String,
)

internal object ArticleOutline {
    const val MIN_VISIBLE_HEADINGS = 3
    const val MAX_HEADING_COUNT = 40

    private const val MAX_TITLE_LENGTH = 160
    private const val MAX_CANDIDATE_JSON_LENGTH = 64 * 1024
    private val whitespace = Regex("\\s+")

    fun build(candidates: List<ArticleHeadingCandidate>): List<ArticleOutlineItem> {
        val boundedCandidates = candidates.take(MAX_HEADING_COUNT)
        val existingIDs = boundedCandidates
            .mapNotNull { it.id?.trim()?.takeIf(String::isNotEmpty) }
            .toSet()
        val claimedExistingIDs = mutableSetOf<String>()
        val usedIDs = mutableSetOf<String>()

        return boundedCandidates
            .asSequence()
            .filter { it.domIndex >= 0 }
            .mapNotNull { candidate ->
                val title = candidate.title
                    .replace(whitespace, " ")
                    .trim()
                    .take(MAX_TITLE_LENGTH)

                if (title.isEmpty()) {
                    return@mapNotNull null
                }

                val existingID = candidate.id?.trim().orEmpty()
                val targetID = if (
                    existingID.isNotEmpty() &&
                    claimedExistingIDs.add(existingID)
                ) {
                    existingID
                } else {
                    generatedID(candidate.domIndex, usedIDs + existingIDs)
                }
                usedIDs += targetID

                ArticleOutlineItem(
                    domIndex = candidate.domIndex,
                    level = candidate.level.coerceIn(1, 6),
                    title = title,
                    targetID = targetID,
                )
            }
            .toList()
    }

    fun shouldShow(items: List<ArticleOutlineItem>): Boolean {
        return items.size >= MIN_VISIBLE_HEADINGS
    }

    fun decodeCandidates(json: String): List<ArticleHeadingCandidate> {
        require(json.length <= MAX_CANDIDATE_JSON_LENGTH)
        return Json.decodeFromString(json)
    }

    fun applyScript(items: List<ArticleOutlineItem>): String {
        val targets = items.map { ArticleHeadingTarget(it.domIndex, it.targetID) }
        return "applyArticleOutline(${Json.encodeToString(targets)})"
    }

    fun jumpScript(targetID: String): String {
        return "scrollToArticleHeading(${Json.encodeToString(targetID)})"
    }

    private fun generatedID(domIndex: Int, usedIDs: Set<String>): String {
        val base = "capy-heading-${domIndex + 1}"
        var candidate = base
        var suffix = 2

        while (candidate in usedIDs) {
            candidate = "$base-$suffix"
            suffix += 1
        }

        return candidate
    }
}
