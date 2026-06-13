package com.capyreader.app.ui.articles.list

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.capyreader.app.R

enum class ArticleHomeDestination {
    FEEDS,
    UNREAD,
    STARRED,
}

@Composable
fun ArticleHomeNavigationBar(
    selectedDestination: ArticleHomeDestination,
    unreadCount: Long,
    onSelectFeeds: () -> Unit,
    onSelectUnread: () -> Unit,
    onSelectStarred: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = colorScheme.onSecondaryContainer,
        selectedTextColor = colorScheme.onSurface,
        indicatorColor = colorScheme.secondaryContainer,
        unselectedIconColor = colorScheme.onSurfaceVariant,
        unselectedTextColor = colorScheme.onSurfaceVariant,
    )

    NavigationBar(
        containerColor = colorScheme.surfaceContainer,
    ) {
        NavigationBarItem(
            selected = selectedDestination == ArticleHomeDestination.FEEDS,
            onClick = onSelectFeeds,
            colors = itemColors,
            icon = {
                Icon(
                    Icons.AutoMirrored.Rounded.List,
                    contentDescription = null,
                )
            },
            label = { Text(stringResource(R.string.feed_nav_drawer_title)) },
        )

        NavigationBarItem(
            selected = selectedDestination == ArticleHomeDestination.UNREAD,
            onClick = onSelectUnread,
            colors = itemColors,
            icon = {
                BadgedBox(
                    badge = {
                        if (unreadCount > 0) {
                            Badge {
                                Text(formatBadgeCount(unreadCount))
                            }
                        }
                    },
                ) {
                    Icon(
                        Icons.Rounded.FiberManualRecord,
                        contentDescription = null,
                    )
                }
            },
            label = { Text(stringResource(R.string.filter_unread)) },
        )

        NavigationBarItem(
            selected = selectedDestination == ArticleHomeDestination.STARRED,
            onClick = onSelectStarred,
            colors = itemColors,
            icon = {
                Icon(
                    Icons.Rounded.Star,
                    contentDescription = null,
                )
            },
            label = { Text(stringResource(R.string.filter_starred)) },
        )
    }
}

private fun formatBadgeCount(count: Long): String {
    return if (count > 99) {
        "99+"
    } else {
        count.toString()
    }
}
