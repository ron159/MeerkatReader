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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.capyreader.app.R
import com.capyreader.app.common.RowItem
import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.preferences.AppTheme
import com.capyreader.app.preferences.ArticleImageCacheCleanupInterval
import com.capyreader.app.preferences.ArticleImageCacheSize
import com.capyreader.app.preferences.ArticleImageDownloadMode
import com.capyreader.app.preferences.ReaderImageVisibility
import com.capyreader.app.preferences.ThemeMode
import com.capyreader.app.ui.articles.MarkReadPosition
import com.capyreader.app.ui.collectChangesWithCurrent
import com.capyreader.app.ui.components.FormSection
import com.capyreader.app.ui.components.TextSwitch
import com.capyreader.app.ui.components.ThemePicker
import com.capyreader.app.ui.settings.PreferenceSelect
import com.capyreader.app.ui.theme.CapyTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun DisplaySettingsPanel(
    viewModel: DisplaySettingsViewModel = koinViewModel(),
    onNavigateToUnreadBadges: () -> Unit = {},
    onNavigateToArticleList: () -> Unit = {},
) {
    val pinArticleBars by viewModel.pinArticleBars.collectChangesWithCurrent()
    val improveTalkback by viewModel.improveTalkback.collectChangesWithCurrent()
    val markReadButtonPosition by viewModel.markReadButtonPosition.collectChangesWithCurrent()
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
        articleImageDownloadMode = viewModel.articleImageDownloadMode,
        updateArticleImageDownloadMode = viewModel::updateArticleImageDownloadMode,
        articleImageCacheSize = viewModel.articleImageCacheSize,
        updateArticleImageCacheSize = viewModel::updateArticleImageCacheSize,
        articleImageCacheCleanupInterval = viewModel.articleImageCacheCleanupInterval,
        updateArticleImageCacheCleanupInterval = viewModel::updateArticleImageCacheCleanupInterval,
        clearArticleImageCache = viewModel::clearArticleImageCache,
        markReadButtonPosition = markReadButtonPosition,
        updateMarkReadButtonPosition = viewModel::updateMarkReadButtonPosition,
        onNavigateToUnreadBadges = onNavigateToUnreadBadges,
        onNavigateToArticleList = onNavigateToArticleList,
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
    articleImageDownloadMode: ArticleImageDownloadMode,
    articleImageCacheSize: ArticleImageCacheSize,
    articleImageCacheCleanupInterval: ArticleImageCacheCleanupInterval,
    markReadButtonPosition: MarkReadPosition,
    updateImageVisibility: (option: ReaderImageVisibility) -> Unit,
    updateArticleImageDownloadMode: (mode: ArticleImageDownloadMode) -> Unit,
    updateArticleImageCacheSize: (size: ArticleImageCacheSize) -> Unit,
    updateArticleImageCacheCleanupInterval: (interval: ArticleImageCacheCleanupInterval) -> Unit,
    clearArticleImageCache: () -> Unit,
    updateMarkReadButtonPosition: (position: MarkReadPosition) -> Unit,
    onNavigateToUnreadBadges: () -> Unit = {},
    onNavigateToArticleList: () -> Unit = {},
) {
    val (isClearImageCacheDialogOpen, setClearImageCacheDialogOpen) = remember { mutableStateOf(false) }

    val onClearImageCacheCancel = {
        setClearImageCacheDialogOpen(false)
    }

    val onRequestClearImageCache = {
        setClearImageCacheDialogOpen(false)
        clearArticleImageCache()
    }

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

            Column {
                SettingsDisclosureRow(
                    title = stringResource(R.string.settings_article_list_title),
                    onClick = onNavigateToArticleList,
                )

                SettingsDisclosureRow(
                    title = stringResource(R.string.settings_panel_unread_counts_title),
                    onClick = onNavigateToUnreadBadges,
                )
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
            PreferenceSelect(
                selected = articleImageDownloadMode,
                update = updateArticleImageDownloadMode,
                options = ArticleImageDownloadMode.entries,
                label = R.string.article_image_download_mode_label,
                optionText = {
                    stringResource(it.translationKey)
                }
            )
            PreferenceSelect(
                selected = articleImageCacheSize,
                update = updateArticleImageCacheSize,
                options = ArticleImageCacheSize.entries,
                label = R.string.article_image_cache_size_label,
                optionText = {
                    stringResource(it.translationKey)
                }
            )
            PreferenceSelect(
                selected = articleImageCacheCleanupInterval,
                update = updateArticleImageCacheCleanupInterval,
                options = ArticleImageCacheCleanupInterval.entries,
                label = R.string.article_image_cache_cleanup_label,
                optionText = {
                    stringResource(it.translationKey)
                }
            )
            RowItem {
                FilledTonalButton(
                    onClick = { setClearImageCacheDialogOpen(true) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.article_image_cache_clear_button))
                }
            }
            RowItem {
                TextSwitch(
                    enabled = enablePinArticleBars,
                    checked = pinArticleBars,
                    onCheckedChange = updatePinArticleBars,
                    title = stringResource(R.string.settings_options_reader_pin_top_toolbar),
                )
            }
        }

        FormSection(title = stringResource(R.string.settings_display_miscellaneous_title)) {
            PreferenceSelect(
                selected = markReadButtonPosition,
                update = updateMarkReadButtonPosition,
                options = MarkReadPosition.entries,
                label = R.string.mark_all_read_button_position,
                optionText = {
                    stringResource(it.translationKey)
                }
            )
        }

        Spacer(Modifier.height(16.dp))
    }

    if (isClearImageCacheDialogOpen) {
        AlertDialog(
            onDismissRequest = onClearImageCacheCancel,
            text = { Text(stringResource(R.string.article_image_cache_clear_message)) },
            dismissButton = {
                TextButton(onClick = onClearImageCacheCancel) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
            confirmButton = {
                TextButton(onClick = onRequestClearImageCache) {
                    Text(stringResource(R.string.article_image_cache_clear_confirm))
                }
            }
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
                articleImageDownloadMode = ArticleImageDownloadMode.WIFI_ONLY,
                updateArticleImageDownloadMode = {},
                articleImageCacheSize = ArticleImageCacheSize.LARGE,
                updateArticleImageCacheSize = {},
                articleImageCacheCleanupInterval = ArticleImageCacheCleanupInterval.WEEKLY,
                updateArticleImageCacheCleanupInterval = {},
                clearArticleImageCache = {},
                enablePinArticleBars = false,
                markReadButtonPosition = MarkReadPosition.TOOLBAR,
                updateMarkReadButtonPosition = {}
            )
        }
    }
}
