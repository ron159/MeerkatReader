package com.capyreader.app.ui.articles.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.capyreader.app.R
import com.capyreader.app.ai.ArticleAiAction
import com.capyreader.app.ai.ArticleAiDisplayState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleAiSheet(
    topState: ArticleAiDisplayState?,
    translationState: ArticleAiDisplayState?,
    onRunAction: (ArticleAiAction, Boolean, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ArticleAiSheetContent(
            topState = topState,
            translationState = translationState,
            onRunAction = onRunAction,
        )
    }
}

@Composable
internal fun ArticleAiSheetContent(
    topState: ArticleAiDisplayState?,
    translationState: ArticleAiDisplayState?,
    onRunAction: (ArticleAiAction, Boolean, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var question by rememberSaveable { mutableStateOf("") }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.article_ai_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.article_ai_privacy_notice),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        AiActionRow(
            label = stringResource(R.string.article_ai_summarize),
            hasResult = topState.hasCompletedResultFor(ArticleAiAction.SUMMARIZE),
            isLoading = topState.isLoading(ArticleAiAction.SUMMARIZE),
            onRun = { onRunAction(ArticleAiAction.SUMMARIZE, false, null) },
            onRegenerate = { onRunAction(ArticleAiAction.SUMMARIZE, true, null) },
        )

        AiActionRow(
            label = stringResource(R.string.article_ai_key_points),
            hasResult = topState.hasCompletedResultFor(ArticleAiAction.KEY_POINTS),
            isLoading = topState.isLoading(ArticleAiAction.KEY_POINTS),
            onRun = { onRunAction(ArticleAiAction.KEY_POINTS, false, null) },
            onRegenerate = { onRunAction(ArticleAiAction.KEY_POINTS, true, null) },
        )

        AiActionRow(
            label = stringResource(R.string.article_ai_translate),
            hasResult = translationState.hasCompletedResultFor(ArticleAiAction.TRANSLATE),
            isLoading = translationState.isLoading(ArticleAiAction.TRANSLATE),
            onRun = { onRunAction(ArticleAiAction.TRANSLATE, false, null) },
            onRegenerate = { onRunAction(ArticleAiAction.TRANSLATE, true, null) },
        )

        AiQuestionRow(
            question = question,
            onQuestionChange = { question = it },
            isLoading = topState.isLoading(ArticleAiAction.QUESTION),
            onRun = {
                val normalizedQuestion = question.trim()
                if (normalizedQuestion.isNotEmpty()) {
                    onRunAction(ArticleAiAction.QUESTION, false, normalizedQuestion)
                }
            },
        )

        Spacer(Modifier.height(8.dp))
    }
}

private fun ArticleAiDisplayState?.hasCompletedResultFor(action: ArticleAiAction): Boolean {
    return this?.let { it.action == action && !it.result.isNullOrBlank() } == true
}

private fun ArticleAiDisplayState?.isLoading(action: ArticleAiAction): Boolean {
    return this?.let { it.action == action && it.isLoading } == true
}

@Composable
private fun AiActionRow(
    label: String,
    hasResult: Boolean,
    isLoading: Boolean,
    onRun: () -> Unit,
    onRegenerate: () -> Unit,
) {
    val loadingDescription = stringResource(R.string.article_ai_loading_short)

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        FilledTonalButton(
            enabled = !isLoading,
            onClick = onRun,
            modifier = Modifier
                .weight(1f)
                .semantics {
                    if (isLoading) {
                        contentDescription = label
                        stateDescription = loadingDescription
                        liveRegion = LiveRegionMode.Polite
                    }
                },
        ) {
            Text(if (isLoading) loadingDescription else label)
        }

        if (hasResult) {
            TextButton(
                enabled = !isLoading,
                onClick = onRegenerate,
            ) {
                Text(stringResource(R.string.article_ai_regenerate))
            }
        }
    }
}

@Composable
private fun AiQuestionRow(
    question: String,
    onQuestionChange: (String) -> Unit,
    isLoading: Boolean,
    onRun: () -> Unit,
) {
    val actionLabel = stringResource(R.string.article_ai_ask)
    val loadingDescription = stringResource(R.string.article_ai_loading_short)
    val focusManager = LocalFocusManager.current

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = question,
            onValueChange = onQuestionChange,
            label = { Text(stringResource(R.string.article_ai_question_placeholder)) },
            modifier = Modifier
                .fillMaxWidth()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || event.key != Key.Tab) {
                        return@onPreviewKeyEvent false
                    }

                    focusManager.moveFocus(
                        if (event.isShiftPressed) {
                            FocusDirection.Previous
                        } else {
                            FocusDirection.Next
                        }
                    )
                },
            minLines = 2,
        )
        FilledTonalButton(
            enabled = question.isNotBlank() && !isLoading,
            onClick = onRun,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    if (isLoading) {
                        contentDescription = actionLabel
                        stateDescription = loadingDescription
                        liveRegion = LiveRegionMode.Polite
                    }
                },
        ) {
            Text(
                if (isLoading) {
                    loadingDescription
                } else {
                    actionLabel
                }
            )
        }
    }
}
