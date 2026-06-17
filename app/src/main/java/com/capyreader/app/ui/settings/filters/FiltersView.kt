package com.capyreader.app.ui.settings.filters

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
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
import androidx.compose.material3.Switch
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
import com.jocmp.capy.ArticleAutomationArticle
import com.jocmp.capy.ArticleRuleCondition
import com.jocmp.capy.ArticleRuleAction
import com.jocmp.capy.ArticleRuleField
import com.jocmp.capy.ArticleRuleOperator
import com.jocmp.capy.RuleMatchMode
import com.jocmp.capy.testAgainst

@Composable
fun FiltersView(
    onAddKeyword: (keyword: String) -> Unit,
    onRemoveKeyword: (keyword: String) -> Unit,
    keywords: List<String>,
    onAddRule: (rule: ArticleAutomationRule) -> Unit,
    onUpdateRule: (rule: ArticleAutomationRule) -> Unit,
    onRemoveRule: (rule: ArticleAutomationRule) -> Unit,
    onMoveRule: (rule: ArticleAutomationRule, direction: Int) -> Unit,
    rules: List<ArticleAutomationRule>,
) {
    val containerColor = CardDefaults.cardColors().containerColor

    var keywordText by remember { mutableStateOf("") }
    var ruleName by remember { mutableStateOf("") }
    var categoryName by remember { mutableStateOf("") }
    var ruleMatchMode by remember { mutableStateOf(RuleMatchMode.ALL) }
    var ruleConditions by remember { mutableStateOf(listOf(emptyCondition())) }
    var ruleActions by remember { mutableStateOf(setOf(ArticleRuleAction.MUTE)) }
    var ruleSampleText by remember { mutableStateOf("") }
    var editingRule by remember { mutableStateOf<ArticleAutomationRule?>(null) }

    fun resetRuleForm() {
        editingRule = null
        ruleName = ""
        categoryName = ""
        ruleMatchMode = RuleMatchMode.ALL
        ruleConditions = listOf(emptyCondition())
        ruleActions = setOf(ArticleRuleAction.MUTE)
        ruleSampleText = ""
    }

    val addKeyword = {
        if (keywordText.isNotBlank()) {
            onAddKeyword(keywordText.trim())
            keywordText = ""
        }
    }

    fun editRule(rule: ArticleAutomationRule) {
        editingRule = rule
        ruleName = rule.name
        categoryName = rule.categoryName
        ruleMatchMode = rule.matchMode
        ruleConditions = rule.conditions.ifEmpty {
            listOf(
                ArticleRuleCondition(
                    field = rule.field,
                    operator = ArticleRuleOperator.CONTAINS,
                    value = rule.pattern,
                )
            )
        }
        ruleActions = rule.actions
    }

    val saveRule = {
        val conditions = ruleConditions
            .map { it.copy(value = it.value.trim()) }
            .filter { it.value.isNotBlank() }
        val primaryCondition = conditions.firstOrNull()

        if (primaryCondition != null && ruleActions.isNotEmpty()) {
            val updatedRule = editingRule?.copy(
                name = ruleName.trim(),
                field = primaryCondition.field,
                pattern = primaryCondition.value,
                matchMode = ruleMatchMode,
                conditions = conditions,
                categoryName = categoryName.trim(),
                actions = ruleActions,
            ) ?: ArticleAutomationRule(
                    name = ruleName.trim(),
                    field = primaryCondition.field,
                    pattern = primaryCondition.value,
                    matchMode = ruleMatchMode,
                    conditions = conditions,
                    categoryName = categoryName.trim(),
                    actions = ruleActions,
                )

            if (editingRule == null) {
                onAddRule(updatedRule)
            } else {
                onUpdateRule(updatedRule)
            }
            resetRuleForm()
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
            rules.forEachIndexed { index, rule ->
                RuleListItem(
                    rule = rule,
                    onEdit = { editRule(rule) },
                    onToggleEnabled = { enabled -> onUpdateRule(rule.copy(enabled = enabled)) },
                    onRemove = { onRemoveRule(rule) },
                    onMoveUp = { onMoveRule(rule, -1) },
                    onMoveDown = { onMoveRule(rule, 1) },
                    canMoveUp = index > 0,
                    canMoveDown = index < rules.lastIndex,
                    containerColor = containerColor,
                )
            }

            Text(
                stringResource(if (editingRule == null) R.string.rules_add_title else R.string.rules_edit_title),
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
            TextButton(
                onClick = { ruleMatchMode = ruleMatchMode.next() },
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Text(ruleMatchMode.label())
            }
            ruleConditions.forEachIndexed { index, condition ->
                RuleConditionRow(
                    condition = condition,
                    canRemove = ruleConditions.size > 1,
                    onUpdate = { updated ->
                        ruleConditions = ruleConditions.mapIndexed { conditionIndex, existing ->
                            if (conditionIndex == index) updated else existing
                        }
                    },
                    onRemove = {
                        ruleConditions = ruleConditions.filterIndexed { conditionIndex, _ ->
                            conditionIndex != index
                        }
                    },
                    onSubmit = saveRule,
                )
            }
            TextButton(
                onClick = { ruleConditions = ruleConditions + emptyCondition() },
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Text(stringResource(R.string.rules_add_condition))
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
            RuleTestBox(
                rule = ruleFromForm(
                    editingRule = editingRule,
                    name = ruleName,
                    matchMode = ruleMatchMode,
                    conditions = ruleConditions,
                    categoryName = categoryName,
                    actions = ruleActions,
                ),
                sampleText = ruleSampleText,
                onSampleTextChange = { ruleSampleText = it },
            )
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                if (editingRule != null) {
                    TextButton(onClick = { resetRuleForm() }) {
                        Text(stringResource(R.string.rules_cancel_edit))
                    }
                }
                IconButton(onClick = { saveRule() }) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(
                            if (editingRule == null) R.string.rules_add_rule else R.string.rules_save_rule
                        )
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
    onEdit: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
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

    val conditionSummary = rule.conditionSummary()

    ListItem(
        colors = ListItemDefaults.colors(containerColor = containerColor),
        headlineContent = { Text(rule.name.ifBlank { rule.pattern }) },
        supportingContent = {
            val disabledLabel = stringResource(R.string.rules_disabled)
            val statusPrefix = if (rule.enabled) "" else "$disabledLabel\n"

            Text("$statusPrefix$conditionSummary\n$actionLabels")
        },
        modifier = Modifier.clickable(onClick = onEdit),
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    enabled = canMoveUp,
                    onClick = onMoveUp,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowUpward,
                        contentDescription = stringResource(R.string.rules_move_rule_up),
                    )
                }
                IconButton(
                    enabled = canMoveDown,
                    onClick = onMoveDown,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowDownward,
                        contentDescription = stringResource(R.string.rules_move_rule_down),
                    )
                }
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = onToggleEnabled,
                    modifier = Modifier.padding(end = 4.dp),
                )
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.rules_remove_rule)
                    )
                }
            }
        }
    )
}

@Composable
private fun RuleConditionRow(
    condition: ArticleRuleCondition,
    canRemove: Boolean,
    onUpdate: (ArticleRuleCondition) -> Unit,
    onRemove: () -> Unit,
    onSubmit: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        TextButton(onClick = { onUpdate(condition.copy(field = condition.field.next())) }) {
            Text(condition.field.label())
        }
        TextButton(onClick = { onUpdate(condition.copy(operator = condition.operator.next())) }) {
            Text(condition.operator.label())
        }
        OutlinedTextField(
            value = condition.value,
            singleLine = true,
            onValueChange = { onUpdate(condition.copy(value = it)) },
            placeholder = { Text(stringResource(R.string.rules_pattern_placeholder)) },
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                imeAction = ImeAction.Default,
            ),
            keyboardActions = KeyboardActions(onAny = { onSubmit() }),
            modifier = Modifier.weight(1f)
        )
        if (canRemove) {
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.rules_remove_condition),
                )
            }
        }
    }
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

@Composable
private fun RuleTestBox(
    rule: ArticleAutomationRule,
    sampleText: String,
    onSampleTextChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = sampleText,
        onValueChange = onSampleTextChange,
        placeholder = { Text(stringResource(R.string.rules_test_sample_placeholder)) },
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth(),
        minLines = 2,
    )

    if (sampleText.isBlank()) {
        return
    }

    val result = rule.testAgainst(
        ArticleAutomationArticle(
            title = sampleText,
            author = sampleText,
            summary = sampleText,
            contentHTML = sampleText,
            feedTitle = sampleText,
            feedURL = sampleText,
        )
    )
    val muteLabel = stringResource(R.string.rules_action_mute)
    val keepLabel = stringResource(R.string.rules_action_keep)
    val markReadLabel = stringResource(R.string.rules_action_mark_read)
    val starLabel = stringResource(R.string.rules_action_star)
    val categorizeLabel = stringResource(R.string.rules_action_categorize)
    val notifyLabel = stringResource(R.string.rules_action_notify)

    fun ArticleRuleAction.testLabel(): String = when (this) {
        ArticleRuleAction.MUTE -> muteLabel
        ArticleRuleAction.KEEP -> keepLabel
        ArticleRuleAction.MARK_READ -> markReadLabel
        ArticleRuleAction.STAR -> starLabel
        ArticleRuleAction.CATEGORIZE -> categorizeLabel
        ArticleRuleAction.NOTIFY -> notifyLabel
    }

    val resultText = if (result.matched) {
        val actions = result.actions
            .sortedBy { it.ordinal }
            .joinToString { it.testLabel() }
            .ifBlank { stringResource(R.string.rules_no_actions) }
        stringResource(R.string.rules_test_matched, actions)
    } else {
        stringResource(R.string.rules_test_not_matched)
    }

    Text(
        text = resultText,
        style = typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

private fun emptyCondition() = ArticleRuleCondition(
    field = ArticleRuleField.ANY,
    operator = ArticleRuleOperator.CONTAINS,
    value = "",
)

private fun ruleFromForm(
    editingRule: ArticleAutomationRule?,
    name: String,
    matchMode: RuleMatchMode,
    conditions: List<ArticleRuleCondition>,
    categoryName: String,
    actions: Set<ArticleRuleAction>,
): ArticleAutomationRule {
    val activeConditions = conditions
        .map { it.copy(value = it.value.trim()) }
        .filter { it.value.isNotBlank() }
    val primaryCondition = activeConditions.firstOrNull() ?: emptyCondition()

    return editingRule?.copy(
        name = name.trim(),
        field = primaryCondition.field,
        pattern = primaryCondition.value,
        matchMode = matchMode,
        conditions = activeConditions,
        categoryName = categoryName.trim(),
        actions = actions,
    ) ?: ArticleAutomationRule(
        name = name.trim(),
        field = primaryCondition.field,
        pattern = primaryCondition.value,
        matchMode = matchMode,
        conditions = activeConditions,
        categoryName = categoryName.trim(),
        actions = actions,
    )
}

private fun ArticleRuleField.next(): ArticleRuleField {
    val fields = ArticleRuleField.entries
    return fields[(ordinal + 1) % fields.size]
}

private fun ArticleRuleOperator.next(): ArticleRuleOperator {
    val operators = ArticleRuleOperator.entries
    return operators[(ordinal + 1) % operators.size]
}

private fun RuleMatchMode.next(): RuleMatchMode {
    val modes = RuleMatchMode.entries
    return modes[(ordinal + 1) % modes.size]
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
private fun ArticleRuleOperator.label(): String {
    return stringResource(
        when (this) {
            ArticleRuleOperator.CONTAINS -> R.string.rules_operator_contains
            ArticleRuleOperator.NOT_CONTAINS -> R.string.rules_operator_not_contains
            ArticleRuleOperator.REGEX -> R.string.rules_operator_regex
            ArticleRuleOperator.EQUALS -> R.string.rules_operator_equals
            ArticleRuleOperator.STARTS_WITH -> R.string.rules_operator_starts_with
            ArticleRuleOperator.ENDS_WITH -> R.string.rules_operator_ends_with
        }
    )
}

@Composable
private fun RuleMatchMode.label(): String {
    return stringResource(
        when (this) {
            RuleMatchMode.ALL -> R.string.rules_match_all
            RuleMatchMode.ANY -> R.string.rules_match_any
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

@Composable
private fun ArticleAutomationRule.conditionSummary(): String {
    val anyFieldLabel = stringResource(R.string.rules_field_any)
    val feedFieldLabel = stringResource(R.string.rules_field_feed)
    val authorFieldLabel = stringResource(R.string.rules_field_author)
    val titleFieldLabel = stringResource(R.string.rules_field_title)
    val contentFieldLabel = stringResource(R.string.rules_field_content)
    val containsLabel = stringResource(R.string.rules_operator_contains)
    val notContainsLabel = stringResource(R.string.rules_operator_not_contains)
    val regexLabel = stringResource(R.string.rules_operator_regex)
    val equalsLabel = stringResource(R.string.rules_operator_equals)
    val startsWithLabel = stringResource(R.string.rules_operator_starts_with)
    val endsWithLabel = stringResource(R.string.rules_operator_ends_with)

    fun ArticleRuleField.summaryLabel(): String = when (this) {
        ArticleRuleField.ANY -> anyFieldLabel
        ArticleRuleField.FEED -> feedFieldLabel
        ArticleRuleField.AUTHOR -> authorFieldLabel
        ArticleRuleField.TITLE -> titleFieldLabel
        ArticleRuleField.CONTENT -> contentFieldLabel
    }

    fun ArticleRuleOperator.summaryLabel(): String = when (this) {
        ArticleRuleOperator.CONTAINS -> containsLabel
        ArticleRuleOperator.NOT_CONTAINS -> notContainsLabel
        ArticleRuleOperator.REGEX -> regexLabel
        ArticleRuleOperator.EQUALS -> equalsLabel
        ArticleRuleOperator.STARTS_WITH -> startsWithLabel
        ArticleRuleOperator.ENDS_WITH -> endsWithLabel
    }

    val ruleConditions = this.conditions.ifEmpty {
        listOf(
            ArticleRuleCondition(
                field = field,
                operator = ArticleRuleOperator.CONTAINS,
                value = pattern,
            )
        )
    }
    val prefix = if (ruleConditions.size > 1) {
        "${matchMode.label()}: "
    } else {
        ""
    }

    return prefix + ruleConditions.take(2).joinToString { condition ->
        "${condition.field.summaryLabel()} ${condition.operator.summaryLabel()} ${condition.value}"
    } + if (ruleConditions.size > 2) {
        " +${ruleConditions.size - 2}"
    } else {
        ""
    }
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
            onUpdateRule = { updatedRule ->
                rules.value = rules.value.map { rule ->
                    if (rule.id == updatedRule.id) updatedRule else rule
                }
            },
            onRemoveRule = {
                rules.value = rules.value.filterNot { rule -> rule.id == it.id }
            },
            onMoveRule = { rule, direction ->
                val currentIndex = rules.value.indexOfFirst { it.id == rule.id }
                if (currentIndex != -1) {
                    val targetIndex = (currentIndex + direction).coerceIn(rules.value.indices)
                    rules.value = rules.value.toMutableList().apply {
                        add(targetIndex, removeAt(currentIndex))
                    }
                }
            },
            rules = rules.value,
        )
    }
}
