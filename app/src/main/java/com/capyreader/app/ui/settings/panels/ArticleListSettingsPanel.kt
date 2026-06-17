package com.capyreader.app.ui.settings.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.capyreader.app.R
import com.capyreader.app.common.RowItem
import com.capyreader.app.preferences.AfterReadAllBehavior
import com.capyreader.app.preferences.ArticleStatusListDisplay
import com.capyreader.app.preferences.DefaultHomeTab
import com.capyreader.app.ui.articles.MarkReadPosition
import com.capyreader.app.ui.collectChangesWithCurrent
import com.capyreader.app.ui.components.FormSection
import com.capyreader.app.ui.components.TextSwitch
import com.capyreader.app.ui.settings.PreferenceSelect
import com.jocmp.capy.articles.SortOrder
import org.koin.androidx.compose.koinViewModel

@Composable
fun ArticleListSettingsPanel(
    viewModel: DisplaySettingsViewModel = koinViewModel(),
    generalViewModel: GeneralSettingsViewModel = koinViewModel(),
    onNavigateToUnreadBadges: () -> Unit = {},
) {
    val markReadButtonPosition by viewModel.markReadButtonPosition.collectChangesWithCurrent()

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.verticalScroll(rememberScrollState()),
    ) {
        ArticleListSettings(
            options = ArticleListOptions(
                imagePreview = viewModel.imagePreview,
                showSummary = viewModel.showSummary,
                fontScale = viewModel.fontScale,
                showFeedIcons = viewModel.showFeedIcons,
                showFeedName = viewModel.showFeedName,
                shortenTitles = viewModel.shortenTitles,
                updateImagePreview = viewModel::updateImagePreview,
                updateSummary = viewModel::updateSummary,
                updateFeedName = viewModel::updateFeedName,
                updateFeedIcons = viewModel::updateFeedIcons,
                updateFontScale = viewModel::updateFontScale,
                updateShortenTitles = viewModel::updateShortenTitles,
            )
        )

        ArticleListBehaviorSettings(
            sortOrder = generalViewModel.sortOrder,
            updateSortOrder = generalViewModel::updateSortOrder,
            defaultHomeTab = generalViewModel.defaultHomeTab,
            updateDefaultHomeTab = generalViewModel::updateDefaultHomeTab,
            unreadDisplay = viewModel.unreadDisplay,
            updateUnreadDisplay = viewModel::updateUnreadDisplay,
            starredDisplay = viewModel.starredDisplay,
            updateStarredDisplay = viewModel::updateStarredDisplay,
            markReadButtonPosition = markReadButtonPosition,
            updateMarkReadButtonPosition = viewModel::updateMarkReadButtonPosition,
            markReadOnScroll = generalViewModel.markReadOnScroll,
            updateMarkReadOnScroll = generalViewModel::updateMarkReadOnScroll,
            confirmMarkAllRead = generalViewModel.confirmMarkAllRead,
            updateConfirmMarkAllRead = generalViewModel::updateConfirmMarkAllRead,
            afterReadAll = generalViewModel.afterReadAll,
            updateAfterReadAll = generalViewModel::updateAfterReadAll,
            onNavigateToUnreadBadges = onNavigateToUnreadBadges,
        )
    }
}

@Composable
private fun ArticleListBehaviorSettings(
    sortOrder: SortOrder,
    updateSortOrder: (SortOrder) -> Unit,
    defaultHomeTab: DefaultHomeTab,
    updateDefaultHomeTab: (DefaultHomeTab) -> Unit,
    unreadDisplay: ArticleStatusListDisplay,
    updateUnreadDisplay: (display: ArticleStatusListDisplay) -> Unit,
    starredDisplay: ArticleStatusListDisplay,
    updateStarredDisplay: (display: ArticleStatusListDisplay) -> Unit,
    markReadButtonPosition: MarkReadPosition,
    updateMarkReadButtonPosition: (position: MarkReadPosition) -> Unit,
    markReadOnScroll: Boolean,
    updateMarkReadOnScroll: (enable: Boolean) -> Unit,
    confirmMarkAllRead: Boolean,
    updateConfirmMarkAllRead: (enable: Boolean) -> Unit,
    afterReadAll: AfterReadAllBehavior,
    updateAfterReadAll: (behavior: AfterReadAllBehavior) -> Unit,
    onNavigateToUnreadBadges: () -> Unit,
) {
    FormSection(title = stringResource(R.string.settings_section_article_list_behavior)) {
        SettingsDisclosureRow(
            title = stringResource(R.string.settings_panel_unread_counts_title),
            onClick = onNavigateToUnreadBadges,
        )
        SortOrderSelect(
            sortOrder,
            updateSortOrder
        )
        PreferenceSelect(
            selected = defaultHomeTab,
            update = updateDefaultHomeTab,
            options = DefaultHomeTab.entries,
            label = R.string.settings_default_home_tab,
            optionText = {
                stringResource(it.translationKey)
            }
        )
        PreferenceSelect(
            selected = unreadDisplay,
            update = updateUnreadDisplay,
            options = ArticleStatusListDisplay.entries,
            label = R.string.settings_unread_display,
            optionText = {
                stringResource(it.translationKey)
            }
        )
        PreferenceSelect(
            selected = starredDisplay,
            update = updateStarredDisplay,
            options = ArticleStatusListDisplay.entries,
            label = R.string.settings_starred_display,
            optionText = {
                stringResource(it.translationKey)
            }
        )
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

    FormSection(title = stringResource(R.string.settings_section_mark_all_as_read)) {
        Column {
            RowItem {
                TextSwitch(
                    onCheckedChange = updateMarkReadOnScroll,
                    checked = markReadOnScroll,
                    title = stringResource(R.string.settings_mark_read_on_scroll),
                )
            }
            RowItem {
                TextSwitch(
                    onCheckedChange = updateConfirmMarkAllRead,
                    checked = confirmMarkAllRead,
                    title = stringResource(R.string.settings_confirm_mark_all_read),
                )
            }
            PreferenceSelect(
                selected = afterReadAll,
                update = updateAfterReadAll,
                options = AfterReadAllBehavior.entries,
                label = R.string.after_read_all_behavior_label,
                optionText = {
                    stringResource(id = it.translationKey)
                }
            )
        }
    }
}
