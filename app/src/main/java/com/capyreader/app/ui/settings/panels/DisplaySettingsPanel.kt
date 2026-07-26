package com.capyreader.app.ui.settings.panels

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.capyreader.app.R
import com.capyreader.app.common.RowItem
import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.preferences.AppTheme
import com.capyreader.app.preferences.ReaderImageVisibility
import com.capyreader.app.preferences.ThemeMode
import com.capyreader.app.tts.ArticleTtsConfiguration
import com.capyreader.app.tts.ArticleTtsVoice
import com.capyreader.app.ui.collectChangesWithCurrent
import com.capyreader.app.ui.components.FormSection
import com.capyreader.app.ui.components.TextSwitch
import com.capyreader.app.ui.components.ThemePicker
import com.capyreader.app.ui.settings.PreferenceSelect
import com.capyreader.app.ui.theme.CapyTheme
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

@Composable
fun DisplaySettingsPanel(
    viewModel: DisplaySettingsViewModel = koinViewModel(),
) {
    val pinArticleBars by viewModel.pinArticleBars.collectChangesWithCurrent()
    val improveTalkback by viewModel.improveTalkback.collectChangesWithCurrent()
    val appTheme by viewModel.appPreferences.appTheme.collectChangesWithCurrent()

    DisplaySettingsPanelView(
        themeMode = viewModel.themeMode,
        updateThemeMode = viewModel::updateThemeMode,
        appTheme = appTheme,
        pureBlackDarkMode = viewModel.pureBlackDarkMode,
        updatePureBlackDarkMode = viewModel::updatePureBlackDarkMode,
        accentColors = viewModel.accentColors,
        updateAccentColors = viewModel::updateAccentColors,
        appPreferences = viewModel.appPreferences,
        updatePinArticleBars = viewModel::updatePinArticleBars,
        pinArticleBars = pinArticleBars,
        enablePinArticleBars = !improveTalkback,
        updateImageVisibility = viewModel::updateImageVisibility,
        imageVisibility = viewModel.imageVisibility,
        ttsLanguageTag = viewModel.ttsLanguageTag,
        ttsLanguageTags = viewModel.ttsLanguageTags,
        updateTtsLanguage = viewModel::updateTtsLanguage,
        ttsVoiceID = viewModel.ttsVoiceID,
        ttsVoices = viewModel.ttsVoices,
        updateTtsVoice = viewModel::updateTtsVoice,
        ttsSpeechRate = viewModel.ttsSpeechRate,
        updateTtsSpeechRate = viewModel::updateTtsSpeechRate,
    )
}

@Composable
fun DisplaySettingsPanelView(
    themeMode: ThemeMode,
    updateThemeMode: (ThemeMode) -> Unit,
    appTheme: AppTheme = AppTheme.default,
    pureBlackDarkMode: Boolean,
    updatePureBlackDarkMode: (Boolean) -> Unit,
    accentColors: Boolean = false,
    updateAccentColors: (Boolean) -> Unit = {},
    appPreferences: AppPreferences?,
    updatePinArticleBars: (enable: Boolean) -> Unit,
    pinArticleBars: Boolean,
    enablePinArticleBars: Boolean,
    imageVisibility: ReaderImageVisibility,
    updateImageVisibility: (option: ReaderImageVisibility) -> Unit,
    ttsLanguageTag: String,
    ttsLanguageTags: List<String>,
    updateTtsLanguage: (String) -> Unit,
    ttsVoiceID: String,
    ttsVoices: List<ArticleTtsVoice>,
    updateTtsVoice: (String) -> Unit,
    ttsSpeechRate: Float,
    updateTtsSpeechRate: (Float) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        FormSection(
            title = stringResource(R.string.theme_menu_label)
        ) {
            RowItem {
                ThemeModeButtons(
                    themeMode = themeMode,
                    updateThemeMode = updateThemeMode
                )
            }

            if (appPreferences != null) {
                ThemePicker(appPreferences = appPreferences)
            }

            RowItem {
                TextSwitch(
                    onCheckedChange = updatePureBlackDarkMode,
                    checked = pureBlackDarkMode,
                    title = stringResource(R.string.settings_pure_black_dark_mode)
                )
            }
            AnimatedVisibility(
                visible = appTheme.supportsFeedAccentColor,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                RowItem {
                    TextSwitch(
                        onCheckedChange = updateAccentColors,
                        checked = accentColors,
                        title = stringResource(R.string.settings_accent_colors)
                    )
                }
            }

        }

        FormSection(
            title = stringResource(R.string.settings_reader_title)
        ) {
            PreferenceSelect(
                selected = imageVisibility,
                update = updateImageVisibility,
                options = ReaderImageVisibility.entries,
                label = R.string.reader_image_visibility_label,
                optionText = {
                    stringResource(it.translationKey)
                }
            )
            ArticleTtsPreferences(
                languageTag = ttsLanguageTag,
                languageTags = ttsLanguageTags,
                onSelectLanguage = updateTtsLanguage,
                voiceID = ttsVoiceID,
                voices = ttsVoices,
                onSelectVoice = updateTtsVoice,
                speechRate = ttsSpeechRate,
                onSelectSpeechRate = updateTtsSpeechRate,
            )
            RowItem {
                TextSwitch(
                    enabled = enablePinArticleBars,
                    checked = pinArticleBars,
                    onCheckedChange = updatePinArticleBars,
                    title = stringResource(R.string.settings_options_reader_pin_top_toolbar),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
internal fun ArticleTtsPreferences(
    languageTag: String,
    languageTags: List<String>,
    onSelectLanguage: (String) -> Unit,
    voiceID: String,
    voices: List<ArticleTtsVoice>,
    onSelectVoice: (String) -> Unit,
    speechRate: Float,
    onSelectSpeechRate: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayLocale = LocalLocale.current.platformLocale

    Column(modifier = modifier) {
        PreferenceSelect(
            selected = languageTag,
            update = onSelectLanguage,
            options = languageTags,
            label = R.string.article_tts_language,
            optionText = { option ->
                if (option.isBlank()) {
                    stringResource(R.string.article_tts_system_default)
                } else {
                    Locale.forLanguageTag(option).getDisplayName(displayLocale)
                }
            },
        )
        PreferenceSelect(
            selected = voiceID,
            update = onSelectVoice,
            options = listOf("") + voices.map(ArticleTtsVoice::id),
            label = R.string.article_tts_voice,
            optionText = { option ->
                val voice = voices.find { it.id == option }
                when {
                    option.isBlank() -> stringResource(R.string.article_tts_engine_default)
                    voice?.requiresNetwork == true -> stringResource(
                        R.string.article_tts_voice_requires_network,
                        voice.id,
                    )
                    else -> voice?.id ?: option
                }
            },
        )
        PreferenceSelect(
            selected = speechRate,
            update = onSelectSpeechRate,
            options = TTS_SPEECH_RATES,
            label = R.string.article_tts_speech_rate,
            optionText = { option ->
                stringResource(
                    R.string.article_tts_speech_rate_value,
                    option.toString().removeSuffix(".0"),
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThemeModeButtons(
    themeMode: ThemeMode,
    updateThemeMode: (ThemeMode) -> Unit
) {
    val options = ThemeMode.entries
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, mode ->
            ToggleButton(
                checked = themeMode == mode,
                onCheckedChange = { updateThemeMode(mode) },
                modifier = Modifier.weight(1f),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                }
            ) {
                Text(stringResource(mode.translationKey))
            }
        }
    }
}

@PreviewLightDark
@Preview
@Composable
private fun DisplaySettingsPanelViewPreview() {
    CapyTheme {
        Surface {
            DisplaySettingsPanelView(
                themeMode = ThemeMode.SYSTEM,
                updateThemeMode = {},
                pureBlackDarkMode = false,
                updatePureBlackDarkMode = {},
                appPreferences = null,
                updatePinArticleBars = {},
                pinArticleBars = false,
                updateImageVisibility = {},
                imageVisibility = ReaderImageVisibility.ALWAYS_SHOW,
                enablePinArticleBars = false,
                ttsLanguageTag = "",
                ttsLanguageTags = listOf(""),
                updateTtsLanguage = {},
                ttsVoiceID = "",
                ttsVoices = emptyList(),
                updateTtsVoice = {},
                ttsSpeechRate = ArticleTtsConfiguration.DEFAULT_SPEECH_RATE,
                updateTtsSpeechRate = {},
            )
        }
    }
}

private val TTS_SPEECH_RATES = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
