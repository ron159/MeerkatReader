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
import androidx.compose.ui.res.stringResource
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
    var question by rememberSaveable { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
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
                hasResult = topState?.action == ArticleAiAction.SUMMARIZE && topState.result != null,
                isLoading = topState?.action == ArticleAiAction.SUMMARIZE && topState.isLoading,
                onRun = { onRunAction(ArticleAiAction.SUMMARIZE, false, null) },
                onRegenerate = { onRunAction(ArticleAiAction.SUMMARIZE, true, null) },
            )

            AiActionRow(
                label = stringResource(R.string.article_ai_key_points),
                hasResult = topState?.action == ArticleAiAction.KEY_POINTS && topState.result != null,
                isLoading = topState?.action == ArticleAiAction.KEY_POINTS && topState.isLoading,
                onRun = { onRunAction(ArticleAiAction.KEY_POINTS, false, null) },
                onRegenerate = { onRunAction(ArticleAiAction.KEY_POINTS, true, null) },
            )

            AiActionRow(
                label = stringResource(R.string.article_ai_translate),
                hasResult = translationState?.result != null,
                isLoading = translationState?.isLoading == true,
                onRun = { onRunAction(ArticleAiAction.TRANSLATE, false, null) },
                onRegenerate = { onRunAction(ArticleAiAction.TRANSLATE, true, null) },
            )

            AiQuestionRow(
                question = question,
                onQuestionChange = { question = it },
                isLoading = topState?.action == ArticleAiAction.QUESTION && topState.isLoading,
                onRun = { onRunAction(ArticleAiAction.QUESTION, false, question) },
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AiActionRow(
    label: String,
    hasResult: Boolean,
    isLoading: Boolean,
    onRun: () -> Unit,
    onRegenerate: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        FilledTonalButton(
            enabled = !isLoading,
            onClick = onRun,
            modifier = Modifier.weight(1f),
        ) {
            Text(if (isLoading) stringResource(R.string.article_ai_loading_short) else label)
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
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = question,
            onValueChange = onQuestionChange,
            label = { Text(stringResource(R.string.article_ai_question_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        FilledTonalButton(
            enabled = question.isNotBlank() && !isLoading,
            onClick = onRun,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (isLoading) {
                    stringResource(R.string.article_ai_loading_short)
                } else {
                    stringResource(R.string.article_ai_ask)
                }
            )
        }
    }
}
