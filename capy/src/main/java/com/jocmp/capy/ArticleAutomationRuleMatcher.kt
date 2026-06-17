package com.jocmp.capy

data class ArticleAutomationRuleTestResult(
    val matched: Boolean,
    val actions: Set<ArticleRuleAction> = emptySet(),
    val categoryName: String? = null,
)

fun ArticleAutomationRule.testAgainst(article: ArticleAutomationArticle): ArticleAutomationRuleTestResult {
    val matched = matchesAutomationArticle(article)

    return ArticleAutomationRuleTestResult(
        matched = matched,
        actions = if (matched) actions else emptySet(),
        categoryName = if (matched && ArticleRuleAction.CATEGORIZE in actions) {
            categoryName.trim().ifBlank { name.trim() }.ifBlank { null }
        } else {
            null
        },
    )
}

fun ArticleAutomationRule.matchesAutomationArticle(article: ArticleAutomationArticle): Boolean {
    val activeConditions = automationConditions()

    if (activeConditions.isEmpty()) {
        return false
    }

    return when (matchMode) {
        RuleMatchMode.ALL -> activeConditions.all { it.matchesAutomationArticle(article) }
        RuleMatchMode.ANY -> activeConditions.any { it.matchesAutomationArticle(article) }
    }
}

fun ArticleAutomationRule.automationConditions(): List<ArticleRuleCondition> {
    val explicitConditions = conditions.filter { it.value.isNotBlank() }

    return explicitConditions.ifEmpty {
        pattern.takeIf { it.isNotBlank() }?.let {
            listOf(
                ArticleRuleCondition(
                    field = field,
                    operator = ArticleRuleOperator.CONTAINS,
                    value = it,
                )
            )
        }.orEmpty()
    }
}

private fun ArticleRuleCondition.matchesAutomationArticle(article: ArticleAutomationArticle): Boolean {
    val values = fieldsFor(field, article)
    val query = value.trim()

    if (query.isEmpty()) {
        return false
    }

    return when (operator) {
        ArticleRuleOperator.CONTAINS -> values.any { it.contains(query, ignoreCase = true) }
        ArticleRuleOperator.NOT_CONTAINS -> values.all { !it.contains(query, ignoreCase = true) }
        ArticleRuleOperator.REGEX -> values.any { matchesRegex(it, query) }
        ArticleRuleOperator.EQUALS -> values.any { it.equals(query, ignoreCase = true) }
        ArticleRuleOperator.STARTS_WITH -> values.any { it.startsWith(query, ignoreCase = true) }
        ArticleRuleOperator.ENDS_WITH -> values.any { it.endsWith(query, ignoreCase = true) }
    }
}

private fun fieldsFor(
    field: ArticleRuleField,
    article: ArticleAutomationArticle,
): List<String> {
    val content = "${article.summary.orEmpty()}\n${article.contentHTML.orEmpty()}"

    return when (field) {
        ArticleRuleField.ANY -> listOf(
            article.feedTitle,
            article.feedURL,
            article.title.orEmpty(),
            article.author.orEmpty(),
            content,
        )

        ArticleRuleField.FEED -> listOf(article.feedTitle, article.feedURL)
        ArticleRuleField.AUTHOR -> listOf(article.author.orEmpty())
        ArticleRuleField.TITLE -> listOf(article.title.orEmpty())
        ArticleRuleField.CONTENT -> listOf(content)
    }
}

private fun matchesRegex(value: String, query: String): Boolean {
    val pattern = if (query.length > 2 && query.startsWith("/") && query.endsWith("/")) {
        query.substring(1, query.lastIndex)
    } else {
        query
    }

    return runCatching {
        Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(value)
    }.getOrDefault(false)
}
