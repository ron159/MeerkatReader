package com.jocmp.capy

import kotlinx.serialization.Serializable

@Serializable
data class ArticleAutomationRule(
    val id: String = RandomUUID.generate(),
    val name: String = "",
    val enabled: Boolean = true,
    val field: ArticleRuleField = ArticleRuleField.ANY,
    val pattern: String = "",
    val categoryName: String = "",
    val actions: Set<ArticleRuleAction> = emptySet(),
)

@Serializable
enum class ArticleRuleField {
    ANY,
    FEED,
    AUTHOR,
    TITLE,
    CONTENT,
}

@Serializable
enum class ArticleRuleAction {
    MUTE,
    KEEP,
    MARK_READ,
    STAR,
    CATEGORIZE,
    NOTIFY,
}
