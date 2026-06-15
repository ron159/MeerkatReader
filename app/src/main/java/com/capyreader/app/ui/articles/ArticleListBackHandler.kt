package com.capyreader.app.ui.articles

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.capyreader.app.common.asState
import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.preferences.BackAction
import com.jocmp.capy.ArticleFilter
import com.jocmp.capy.Folder
import org.koin.compose.koinInject

@Composable
fun ArticleListBackHandler(
    filter: ArticleFilter,
    returnFeedsToStatusHome: Boolean,
    onRequestFilter: () -> Unit,
    onRequestFolder: (folder: Folder) -> Unit,
    onRequestFeeds: () -> Unit,
    appPreferences: AppPreferences = koinInject(),
    enabled: Boolean,
) {
    val backAction by appPreferences.articleListOptions.backAction.asState()

    if (!enabled) {
        return
    }

    BackHandler(backAction == BackAction.OPEN_DRAWER && filter is ArticleFilter.Articles) {
        onRequestFeeds()
    }

    BackHandler(filter !is ArticleFilter.Articles) {
        when (filter) {
            is ArticleFilter.Feeds -> {
                val folderTitle = filter.folderTitle
                if (folderTitle != null && !returnFeedsToStatusHome) {
                    onRequestFolder(Folder(folderTitle))
                } else {
                    onRequestFilter()
                }
            }
            else -> onRequestFilter()
        }
    }
}
