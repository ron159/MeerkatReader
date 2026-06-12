package com.capyreader.app.ui.settings.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.capyreader.app.R
import com.capyreader.app.common.RowItem
import com.capyreader.app.preferences.AiProvider
import com.capyreader.app.preferences.AiTranslationMode
import com.capyreader.app.ui.components.FormSection
import com.capyreader.app.ui.components.TextSwitch
import com.capyreader.app.ui.settings.PreferenceSelect
import org.koin.androidx.compose.koinViewModel

@Composable
fun AiSettingsPanel(
    viewModel: AiSettingsViewModel = koinViewModel(),
) {
    AiSettingsPanelView(
        enabled = viewModel.enabled,
        updateEnabled = viewModel::updateEnabled,
        provider = viewModel.provider,
        updateProvider = viewModel::updateProvider,
        baseUrl = viewModel.baseUrl,
        updateBaseUrl = viewModel::updateBaseUrl,
        apiKey = viewModel.apiKey,
        updateApiKey = viewModel::updateApiKey,
        model = viewModel.model,
        updateModel = viewModel::updateModel,
        language = viewModel.language,
        updateLanguage = viewModel::updateLanguage,
        translationMode = viewModel.translationMode,
        updateTranslationMode = viewModel::updateTranslationMode,
        translatePrompt = viewModel.translatePrompt,
        updateTranslatePrompt = viewModel::updateTranslatePrompt,
        summarizePrompt = viewModel.summarizePrompt,
        updateSummarizePrompt = viewModel::updateSummarizePrompt,
        keyPointsPrompt = viewModel.keyPointsPrompt,
        updateKeyPointsPrompt = viewModel::updateKeyPointsPrompt,
    )
}

@Composable
fun AiSettingsPanelView(
    enabled: Boolean,
    updateEnabled: (Boolean) -> Unit,
    provider: AiProvider,
    updateProvider: (AiProvider) -> Unit,
    baseUrl: String,
    updateBaseUrl: (String) -> Unit,
    apiKey: String,
    updateApiKey: (String) -> Unit,
    model: String,
    updateModel: (String) -> Unit,
    language: String,
    updateLanguage: (String) -> Unit,
    translationMode: AiTranslationMode,
    updateTranslationMode: (AiTranslationMode) -> Unit,
    translatePrompt: String,
    updateTranslatePrompt: (String) -> Unit,
    summarizePrompt: String,
    updateSummarizePrompt: (String) -> Unit,
    keyPointsPrompt: String,
    updateKeyPointsPrompt: (String) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        FormSection(title = stringResource(R.string.settings_panel_ai_title)) {
            RowItem {
                TextSwitch(
                    checked = enabled,
                    onCheckedChange = updateEnabled,
                    title = stringResource(R.string.ai_settings_enabled),
                    subtitle = stringResource(R.string.ai_settings_privacy_notice),
                )
            }

            PreferenceSelect(
                selected = provider,
                update = updateProvider,
                options = AiProvider.entries,
                label = R.string.ai_settings_provider,
                optionText = {
                    stringResource(it.translationKey)
                }
            )

            RowItem {
                SettingsTextField(
                    value = baseUrl,
                    onValueChange = updateBaseUrl,
                    label = stringResource(R.string.ai_settings_base_url),
                    keyboardType = KeyboardType.Uri,
                )
            }

            RowItem {
                SettingsTextField(
                    value = apiKey,
                    onValueChange = updateApiKey,
                    label = stringResource(R.string.ai_settings_api_key),
                    keyboardType = KeyboardType.Password,
                    isPassword = true,
                )
            }

            RowItem {
                SettingsTextField(
                    value = model,
                    onValueChange = updateModel,
                    label = stringResource(R.string.ai_settings_model),
                )
            }

            RowItem {
                SettingsTextField(
                    value = language,
                    onValueChange = updateLanguage,
                    label = stringResource(R.string.ai_settings_language),
                )
            }

            PreferenceSelect(
                selected = translationMode,
                update = updateTranslationMode,
                options = AiTranslationMode.entries,
                label = R.string.ai_settings_translation_mode,
                optionText = {
                    stringResource(it.translationKey)
                }
            )
        }

        FormSection(title = stringResource(R.string.ai_settings_prompts)) {
            RowItem {
                SettingsTextField(
                    value = summarizePrompt,
                    onValueChange = updateSummarizePrompt,
                    label = stringResource(R.string.ai_settings_summary_prompt),
                    singleLine = false,
                )
            }

            RowItem {
                SettingsTextField(
                    value = translatePrompt,
                    onValueChange = updateTranslatePrompt,
                    label = stringResource(R.string.ai_settings_translation_prompt),
                    singleLine = false,
                )
            }

            RowItem {
                SettingsTextField(
                    value = keyPointsPrompt,
                    onValueChange = updateKeyPointsPrompt,
                    label = stringResource(R.string.ai_settings_key_points_prompt),
                    singleLine = false,
                )
            }

            RowItem {
                Text(
                    text = stringResource(R.string.ai_settings_prompt_variables),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        FormSection(title = stringResource(R.string.settings_section_privacy)) {
            RowItem {
                Text(
                    text = stringResource(R.string.ai_settings_privacy_detail),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
    )
}
