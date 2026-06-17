package com.capyreader.app.ui.settings.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.capyreader.app.R
import com.capyreader.app.common.RowItem
import com.capyreader.app.preferences.ArticleImageCacheCleanupInterval
import com.capyreader.app.preferences.ArticleImageCacheSize
import com.capyreader.app.preferences.ArticleImageDownloadMode
import com.capyreader.app.ui.components.FormSection
import com.capyreader.app.ui.components.TextSwitch
import com.capyreader.app.ui.settings.PreferenceSelect
import com.jocmp.capy.accounts.AutoDelete
import org.koin.androidx.compose.koinViewModel

@Composable
fun DataStorageSettingsPanel(
    generalViewModel: GeneralSettingsViewModel = koinViewModel(),
    displayViewModel: DisplaySettingsViewModel = koinViewModel(),
) {
    DataStorageSettingsPanelView(
        offlineReadingEnabled = generalViewModel.offlineReadingEnabled,
        updateOfflineReadingEnabled = generalViewModel::updateOfflineReadingEnabled,
        offlineDownloadOnWiFiOnly = generalViewModel.offlineDownloadOnWiFiOnly,
        updateOfflineDownloadOnWiFiOnly = generalViewModel::updateOfflineDownloadOnWiFiOnly,
        offlineIncludeFullContent = generalViewModel.offlineIncludeFullContent,
        updateOfflineIncludeFullContent = generalViewModel::updateOfflineIncludeFullContent,
        offlineIncludeImages = generalViewModel.offlineIncludeImages,
        updateOfflineIncludeImages = generalViewModel::updateOfflineIncludeImages,
        offlineIncludeAudio = generalViewModel.offlineIncludeAudio,
        updateOfflineIncludeAudio = generalViewModel::updateOfflineIncludeAudio,
        offlineStorageLimitMegabytes = generalViewModel.offlineStorageLimitMegabytes,
        updateOfflineStorageLimitMegabytes = generalViewModel::updateOfflineStorageLimitMegabytes,
        offlinePreserveStarred = generalViewModel.offlinePreserveStarred,
        updateOfflinePreserveStarred = generalViewModel::updateOfflinePreserveStarred,
        offlinePreserveSavedForLater = generalViewModel.offlinePreserveSavedForLater,
        updateOfflinePreserveSavedForLater = generalViewModel::updateOfflinePreserveSavedForLater,
        offlinePreserveRecentlyOpened = generalViewModel.offlinePreserveRecentlyOpened,
        updateOfflinePreserveRecentlyOpened = generalViewModel::updateOfflinePreserveRecentlyOpened,
        offlinePreserveFeedOffline = generalViewModel.offlinePreserveFeedOffline,
        updateOfflinePreserveFeedOffline = generalViewModel::updateOfflinePreserveFeedOffline,
        articleImageDownloadMode = displayViewModel.articleImageDownloadMode,
        updateArticleImageDownloadMode = displayViewModel::updateArticleImageDownloadMode,
        articleImageCacheSize = displayViewModel.articleImageCacheSize,
        updateArticleImageCacheSize = displayViewModel::updateArticleImageCacheSize,
        articleImageCacheCleanupInterval = displayViewModel.articleImageCacheCleanupInterval,
        updateArticleImageCacheCleanupInterval = displayViewModel::updateArticleImageCacheCleanupInterval,
        clearArticleImageCache = displayViewModel::clearArticleImageCache,
        autoDelete = generalViewModel.autoDelete,
        updateAutoDelete = generalViewModel::updateAutoDelete,
        onClearArticles = generalViewModel::clearAllArticles,
        onClearOfflinePackages = generalViewModel::clearOfflinePackages,
        onClearReadingProgress = generalViewModel::clearReadingProgress,
        onClearRuleMatchHistory = generalViewModel::clearRuleMatchHistory,
    )
}

@Composable
private fun DataStorageSettingsPanelView(
    offlineReadingEnabled: Boolean,
    updateOfflineReadingEnabled: (enabled: Boolean) -> Unit,
    offlineDownloadOnWiFiOnly: Boolean,
    updateOfflineDownloadOnWiFiOnly: (enabled: Boolean) -> Unit,
    offlineIncludeFullContent: Boolean,
    updateOfflineIncludeFullContent: (enabled: Boolean) -> Unit,
    offlineIncludeImages: Boolean,
    updateOfflineIncludeImages: (enabled: Boolean) -> Unit,
    offlineIncludeAudio: Boolean,
    updateOfflineIncludeAudio: (enabled: Boolean) -> Unit,
    offlineStorageLimitMegabytes: String,
    updateOfflineStorageLimitMegabytes: (String) -> Unit,
    offlinePreserveStarred: Boolean,
    updateOfflinePreserveStarred: (Boolean) -> Unit,
    offlinePreserveSavedForLater: Boolean,
    updateOfflinePreserveSavedForLater: (Boolean) -> Unit,
    offlinePreserveRecentlyOpened: Boolean,
    updateOfflinePreserveRecentlyOpened: (Boolean) -> Unit,
    offlinePreserveFeedOffline: Boolean,
    updateOfflinePreserveFeedOffline: (Boolean) -> Unit,
    articleImageDownloadMode: ArticleImageDownloadMode,
    updateArticleImageDownloadMode: (mode: ArticleImageDownloadMode) -> Unit,
    articleImageCacheSize: ArticleImageCacheSize,
    updateArticleImageCacheSize: (size: ArticleImageCacheSize) -> Unit,
    articleImageCacheCleanupInterval: ArticleImageCacheCleanupInterval,
    updateArticleImageCacheCleanupInterval: (interval: ArticleImageCacheCleanupInterval) -> Unit,
    clearArticleImageCache: () -> Unit,
    autoDelete: AutoDelete,
    updateAutoDelete: (AutoDelete) -> Unit,
    onClearArticles: () -> Unit,
    onClearOfflinePackages: () -> Unit,
    onClearReadingProgress: () -> Unit,
    onClearRuleMatchHistory: () -> Unit,
) {
    val (isClearArticlesDialogOpen, setClearArticlesDialogOpen) = remember { mutableStateOf(false) }
    val (isClearImageCacheDialogOpen, setClearImageCacheDialogOpen) = remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        withFrameNanos { }
        focusManager.clearFocus(force = true)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        FormSection(title = stringResource(R.string.settings_section_offline_reading)) {
            Column {
                RowItem {
                    TextSwitch(
                        checked = offlineReadingEnabled,
                        onCheckedChange = updateOfflineReadingEnabled,
                        title = stringResource(R.string.settings_offline_reading_enabled),
                    )
                }
                RowItem {
                    TextSwitch(
                        checked = offlineDownloadOnWiFiOnly,
                        onCheckedChange = updateOfflineDownloadOnWiFiOnly,
                        title = stringResource(R.string.settings_offline_download_on_wifi_only),
                    )
                }
                RowItem {
                    TextSwitch(
                        checked = offlineIncludeFullContent,
                        onCheckedChange = updateOfflineIncludeFullContent,
                        title = stringResource(R.string.settings_offline_include_full_content),
                    )
                }
                RowItem {
                    TextSwitch(
                        checked = offlineIncludeImages,
                        onCheckedChange = updateOfflineIncludeImages,
                        title = stringResource(R.string.settings_offline_include_images),
                    )
                }
                RowItem {
                    TextSwitch(
                        checked = offlineIncludeAudio,
                        onCheckedChange = updateOfflineIncludeAudio,
                        title = stringResource(R.string.settings_offline_include_audio),
                    )
                }
                RowItem {
                    OutlinedTextField(
                        value = offlineStorageLimitMegabytes,
                        onValueChange = updateOfflineStorageLimitMegabytes,
                        label = { Text(stringResource(R.string.settings_offline_storage_limit_mb)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                RowItem {
                    TextSwitch(
                        checked = offlinePreserveStarred,
                        onCheckedChange = updateOfflinePreserveStarred,
                        title = stringResource(R.string.settings_offline_preserve_starred),
                    )
                }
                RowItem {
                    TextSwitch(
                        checked = offlinePreserveSavedForLater,
                        onCheckedChange = updateOfflinePreserveSavedForLater,
                        title = stringResource(R.string.settings_offline_preserve_saved_for_later),
                    )
                }
                RowItem {
                    TextSwitch(
                        checked = offlinePreserveRecentlyOpened,
                        onCheckedChange = updateOfflinePreserveRecentlyOpened,
                        title = stringResource(R.string.settings_offline_preserve_recently_opened),
                    )
                }
                RowItem {
                    TextSwitch(
                        checked = offlinePreserveFeedOffline,
                        onCheckedChange = updateOfflinePreserveFeedOffline,
                        title = stringResource(R.string.settings_offline_preserve_feed_offline),
                    )
                }
            }
        }

        FormSection(title = stringResource(R.string.settings_section_image_cache)) {
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
        }

        FormSection(title = stringResource(R.string.settings_section_retention_cleanup)) {
            AutoDeleteMenu(
                updateAutoDelete = updateAutoDelete,
                autoDelete = autoDelete,
            )

            RowItem {
                FilledTonalButton(
                    onClick = { setClearArticlesDialogOpen(true) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_clear_all_articles_button))
                }
            }
            RowItem {
                FilledTonalButton(
                    onClick = onClearOfflinePackages,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_clear_offline_packages_button))
                }
            }
            RowItem {
                FilledTonalButton(
                    onClick = onClearReadingProgress,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_clear_reading_progress_button))
                }
            }
            RowItem {
                FilledTonalButton(
                    onClick = onClearRuleMatchHistory,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_clear_rule_match_history_button))
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }

    if (isClearArticlesDialogOpen) {
        AlertDialog(
            onDismissRequest = { setClearArticlesDialogOpen(false) },
            text = { Text(stringResource(R.string.settings_clear_all_articles_text)) },
            dismissButton = {
                TextButton(onClick = { setClearArticlesDialogOpen(false) }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        setClearArticlesDialogOpen(false)
                        onClearArticles()
                    }
                ) {
                    Text(text = stringResource(R.string.settings_clear_all_articles_confirm))
                }
            }
        )
    }

    if (isClearImageCacheDialogOpen) {
        AlertDialog(
            onDismissRequest = { setClearImageCacheDialogOpen(false) },
            text = { Text(stringResource(R.string.article_image_cache_clear_message)) },
            dismissButton = {
                TextButton(onClick = { setClearImageCacheDialogOpen(false) }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        setClearImageCacheDialogOpen(false)
                        clearArticleImageCache()
                    }
                ) {
                    Text(stringResource(R.string.article_image_cache_clear_confirm))
                }
            }
        )
    }
}
