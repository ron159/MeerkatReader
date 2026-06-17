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
    val matchMode: RuleMatchMode = RuleMatchMode.ALL,
    val conditions: List<ArticleRuleCondition> = emptyList(),
)

@Serializable
data class ArticleRuleCondition(
    val field: ArticleRuleField = ArticleRuleField.ANY,
    val operator: ArticleRuleOperator = ArticleRuleOperator.CONTAINS,
    val value: String = "",
)

@Serializable
enum class RuleMatchMode {
    ALL,
    ANY,
}

@Serializable
enum class ArticleRuleField {
    ANY,
    FEED,
    AUTHOR,
    TITLE,
    CONTENT,
}

@Serializable
enum class ArticleRuleOperator {
    CONTAINS,
    NOT_CONTAINS,
    REGEX,
    EQUALS,
    STARTS_WITH,
    ENDS_WITH,
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
