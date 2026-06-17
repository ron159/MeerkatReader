package com.capyreader.app.ui.settings.filters

import androidx.compose.runtime.compositionLocalOf
import com.jocmp.capy.ArticleAutomationRule

val LocalFilterKeywords = compositionLocalOf { FilterKeywords() }

data class FilterKeywords(
    val add: (keyword: String) -> Unit = {},
    val remove: (keyword: String) -> Unit = {},
    val keywords: List<String> = emptyList(),
    val addRule: (rule: ArticleAutomationRule) -> Unit = {},
    val updateRule: (rule: ArticleAutomationRule) -> Unit = {},
    val removeRule: (rule: ArticleAutomationRule) -> Unit = {},
    val moveRule: (rule: ArticleAutomationRule, direction: Int) -> Unit = { _, _ -> },
    val rules: List<ArticleAutomationRule> = emptyList(),
)
