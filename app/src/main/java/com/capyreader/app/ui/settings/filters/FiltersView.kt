package com.capyreader.app.ui.settings.filters

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.capyreader.app.R
import com.capyreader.app.ui.theme.CapyTheme
import com.jocmp.capy.ArticleAutomationRule
import com.jocmp.capy.ArticleRuleAction
import com.jocmp.capy.ArticleRuleField

@Composable
fun FiltersView(
    onAddKeyword: (keyword: String) -> Unit,
    onRemoveKeyword: (keyword: String) -> Unit,
    keywords: List<String>,
    onAddRule: (rule: ArticleAutomationRule) -> Unit,
    onRemoveRule: (rule: ArticleAutomationRule) -> Unit,
    rules: List<ArticleAutomationRule>,
) {
    val containerColor = CardDefaults.cardColors().containerColor

    var keywordText by remember { mutableStateOf("") }
    var ruleName by remember { mutableStateOf("") }
    var rulePattern by remember { mutableStateOf("") }
    var categoryName by remember { mutableStateOf("") }
    var ruleField by remember { mutableStateOf(ArticleRuleField.ANY) }
    var ruleActions by remember { mutableStateOf(setOf(ArticleRuleAction.MUTE)) }

    val addKeyword = {
        if (keywordText.isNotBlank()) {
            onAddKeyword(keywordText.trim())
            keywordText = ""
        }
    }

    val addRule = {
        if (rulePattern.isNotBlank() && ruleActions.isNotEmpty()) {
            onAddRule(
                ArticleAutomationRule(
                    name = ruleName.trim(),
                    field = ruleField,
                    pattern = rulePattern.trim(),
                    categoryName = categoryName.trim(),
                    actions = ruleActions,
                )
            )
            ruleName = ""
            rulePattern = ""
            categoryName = ""
            ruleField = ArticleRuleField.ANY
            ruleActions = setOf(ArticleRuleAction.MUTE)
        }
    }

    Column(
        Modifier
            .background(containerColor)
            .heightIn(max = 600.dp)
            .imePadding()
    ) {
        Text(
            stringResource(R.string.filters_title),
            style = typography.headlineSmall,
            modifier = Modifier
                .padding(top = 24.dp, bottom = 8.dp)
                .padding(horizontal = 16.dp)
        )
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = 240.dp)
                .weight(0.1f)
        ) {
            if (rules.isNotEmpty()) {
                Text(
                    stringResource(R.string.rules_section_title),
                    style = typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            rules.forEach { rule ->
                RuleListItem(
                    rule = rule,
                    onRemove = { onRemoveRule(rule) },
                    containerColor = containerColor,
                )
            }

            Text(
                stringResource(R.string.rules_add_title),
                style = typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            OutlinedTextField(
                value = ruleName,
                singleLine = true,
                onValueChange = { ruleName = it },
                placeholder = { Text(stringResource(R.string.rules_name_placeholder)) },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .fillMaxWidth()
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                TextButton(onClick = { ruleField = ruleField.next() }) {
                    Text(ruleField.label())
                }
                OutlinedTextField(
                    value = rulePattern,
                    singleLine = true,
                    onValueChange = { rulePattern = it },
                    placeholder = { Text(stringResource(R.string.rules_pattern_placeholder)) },
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Default,
                    ),
                    keyboardActions = KeyboardActions(onAny = { addRule() }),
                    modifier = Modifier.weight(1f)
                )
            }
            ArticleRuleAction.entries.forEach { action ->
                RuleActionCheckbox(
                    action = action,
                    checked = action in ruleActions,
                    onCheckedChange = { checked ->
                        ruleActions = if (checked) {
                            ruleActions + action
                        } else {
                            ruleActions - action
                        }
                    }
                )
            }
            if (ArticleRuleAction.CATEGORIZE in ruleActions) {
                OutlinedTextField(
                    value = categoryName,
                    singleLine = true,
                    onValueChange = { categoryName = it },
                    placeholder = { Text(stringResource(R.string.rules_category_placeholder)) },
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .fillMaxWidth()
                )
            }
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                IconButton(onClick = { addRule() }) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.rules_add_rule)
                    )
                }
            }

            if (keywords.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    stringResource(R.string.filters_legacy_keywords_title),
                    style = typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                keywords.forEach { keyword ->
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = containerColor),
                        headlineContent = { Text(keyword) },
                        supportingContent = { Text(stringResource(R.string.filters_legacy_keywords_summary)) },
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    onRemoveKeyword(keyword)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.filters_remove_keyword)
                                )
                            }
                        }
                    )
                }
            }
        }
        HorizontalDivider()
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 16.dp, bottom = 16.dp, start = 16.dp, end = 8.dp)
        ) {
            OutlinedTextField(
                value = keywordText,
                singleLine = true,
                onValueChange = { keywordText = it },
                placeholder = { Text(stringResource(R.string.filters_add_keyword)) },
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Default,
                ),
                keyboardActions = KeyboardActions(onAny = { addKeyword() }),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            IconButton(onClick = { addKeyword() }) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.filters_add_keyword)
                )
            }
        }
    }
}

@Composable
private fun RuleListItem(
    rule: ArticleAutomationRule,
    onRemove: () -> Unit,
    containerColor: androidx.compose.ui.graphics.Color,
) {
    val actionLabels = if (rule.actions.isEmpty()) {
        stringResource(R.string.rules_no_actions)
    } else {
        val muteLabel = stringResource(R.string.rules_action_mute)
        val keepLabel = stringResource(R.string.rules_action_keep)
        val markReadLabel = stringResource(R.string.rules_action_mark_read)
        val starLabel = stringResource(R.string.rules_action_star)
        val categorizeLabel = stringResource(R.string.rules_action_categorize)
        val notifyLabel = stringResource(R.string.rules_action_notify)

        rule.actions.sortedBy { it.ordinal }.joinToString { action ->
            when (action) {
                ArticleRuleAction.MUTE -> muteLabel
                ArticleRuleAction.KEEP -> keepLabel
                ArticleRuleAction.MARK_READ -> markReadLabel
                ArticleRuleAction.STAR -> starLabel
                ArticleRuleAction.CATEGORIZE -> categorizeLabel
                ArticleRuleAction.NOTIFY -> notifyLabel
            }
        }
    }

    val fieldLabel = when (rule.field) {
        ArticleRuleField.ANY -> stringResource(R.string.rules_field_any)
        ArticleRuleField.FEED -> stringResource(R.string.rules_field_feed)
        ArticleRuleField.AUTHOR -> stringResource(R.string.rules_field_author)
        ArticleRuleField.TITLE -> stringResource(R.string.rules_field_title)
        ArticleRuleField.CONTENT -> stringResource(R.string.rules_field_content)
    }

    ListItem(
        colors = ListItemDefaults.colors(containerColor = containerColor),
        headlineContent = { Text(rule.name.ifBlank { rule.pattern }) },
        supportingContent = {
            Text("$fieldLabel: ${rule.pattern}\n$actionLabels")
        },
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.rules_remove_rule)
                )
            }
        }
    )
}

@Composable
private fun RuleActionCheckbox(
    action: ArticleRuleAction,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
        Text(action.label())
    }
}

private fun ArticleRuleField.next(): ArticleRuleField {
    val fields = ArticleRuleField.entries
    return fields[(ordinal + 1) % fields.size]
}

@Composable
private fun ArticleRuleField.label(): String {
    return stringResource(
        when (this) {
            ArticleRuleField.ANY -> R.string.rules_field_any
            ArticleRuleField.FEED -> R.string.rules_field_feed
            ArticleRuleField.AUTHOR -> R.string.rules_field_author
            ArticleRuleField.TITLE -> R.string.rules_field_title
            ArticleRuleField.CONTENT -> R.string.rules_field_content
        }
    )
}

@Composable
private fun ArticleRuleAction.label(): String {
    return stringResource(
        when (this) {
            ArticleRuleAction.MUTE -> R.string.rules_action_mute
            ArticleRuleAction.KEEP -> R.string.rules_action_keep
            ArticleRuleAction.MARK_READ -> R.string.rules_action_mark_read
            ArticleRuleAction.STAR -> R.string.rules_action_star
            ArticleRuleAction.CATEGORIZE -> R.string.rules_action_categorize
            ArticleRuleAction.NOTIFY -> R.string.rules_action_notify
        }
    )
}

@Preview
@Composable
fun FiltersViewPreview() {
    val keywords = remember { mutableSetOf("Advertisement", "Sponsored Post") }
    val rules = remember {
        mutableStateOf(
            listOf(
                ArticleAutomationRule(
                    name = "Important authors",
                    field = ArticleRuleField.AUTHOR,
                    pattern = "Ada Lovelace",
                    categoryName = "Research",
                    actions = setOf(
                        ArticleRuleAction.STAR,
                        ArticleRuleAction.CATEGORIZE,
                        ArticleRuleAction.NOTIFY,
                    ),
                )
            )
        )
    }

    CapyTheme {
        FiltersView(
            onAddKeyword = {
                keywords.add(it)
            },
            onRemoveKeyword = {
                keywords.remove(it)
            },
            keywords = keywords.toList(),
            onAddRule = {
                rules.value = rules.value + it
            },
            onRemoveRule = {
                rules.value = rules.value.filterNot { rule -> rule.id == it.id }
            },
            rules = rules.value,
        )
    }
}
