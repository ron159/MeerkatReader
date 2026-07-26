package com.capyreader.app.ui.articles.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.capyreader.app.R
import com.capyreader.app.common.shareLink
import com.capyreader.app.ui.LocalUnreadCount
import com.capyreader.app.ui.articles.LocalArticleActions
import com.capyreader.app.ui.components.ArticleAction
import com.capyreader.app.ui.components.buildCopyToClipboard
import com.capyreader.app.ui.components.readAction
import com.capyreader.app.ui.components.starAction
import com.capyreader.app.ui.fixtures.ArticleSample
import com.jocmp.capy.Article
import com.jocmp.capy.ArticleIntegrationExportState
import com.jocmp.capy.ArticleOfflinePackageState
import com.jocmp.capy.MarkRead
import com.jocmp.capy.MarkRead.After
import com.jocmp.capy.MarkRead.Before
import com.jocmp.capy.persistence.ArticleIntegrationExportRecord
import com.jocmp.capy.persistence.ArticleOfflinePackageRecord

@Composable
fun ArticleActionMenu(
    expanded: Boolean,
    article: Article,
    index: Int,
    showLabels: Boolean = false,
    showSaveForLater: Boolean = false,
    onMarkAllRead: (range: MarkRead) -> Unit = {},
    onOpenLabels: () -> Unit = {},
    onSaveForLater: (url: String) -> Unit = {},
    onMuteFeed: () -> Unit = {},
    onMuteSimilar: () -> Unit = {},
    onNotifyAuthor: () -> Unit = {},
    onShowAutomationHistory: () -> Unit = {},
    onDownloadOffline: () -> Unit = {},
    onRetryOffline: () -> Unit = {},
    onRemoveOffline: () -> Unit = {},
    offlinePackageRecord: ArticleOfflinePackageRecord? = null,
    showWallabag: Boolean = false,
    wallabagExportRecord: ArticleIntegrationExportRecord? = null,
    onExportWallabag: () -> Unit = {},
    onDismissRequest: () -> Unit = {},
) {
    val unreadCount = LocalUnreadCount.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { onDismissRequest() },
        offset = DpOffset(x = 16.dp, y = 0.dp)
    ) {
        ToggleStarMenuItem(onDismissRequest, article)
        ToggleReadMenuItem(onDismissRequest, article)
        if (showSaveForLater) {
            SaveForLaterMenuItem(onDismissRequest, article, onSaveForLater)
        }
        if (showLabels) {
            LabelMenuItem(onDismissRequest, onOpenLabels)
        }
        DownloadOfflineMenuItem(onDismissRequest, onDownloadOffline)
        if (offlinePackageRecord?.state == ArticleOfflinePackageState.FAILED) {
            OfflineFailureMenuItem(onDismissRequest, offlinePackageRecord)
            RetryOfflineMenuItem(onDismissRequest, onRetryOffline)
        }
        RemoveOfflineMenuItem(onDismissRequest, onRemoveOffline)
        if (showWallabag && article.url != null) {
            val exportFailure = wallabagExportRecord
                ?.takeIf {
                    it.state == ArticleIntegrationExportState.FAILED ||
                        !it.errorMessage.isNullOrBlank()
                }
            if (exportFailure != null) {
                WallabagFailureMenuItem(exportFailure)
            }
            WallabagMenuItem(
                onDismissRequest = onDismissRequest,
                record = wallabagExportRecord,
                onExportWallabag = onExportWallabag,
            )
        }
        if (!article.feedURL.isNullOrBlank() || article.feedName.isNotBlank()) {
            MuteFeedMenuItem(onDismissRequest, onMuteFeed)
        }
        if (article.title.isNotBlank()) {
            MuteSimilarMenuItem(onDismissRequest, onMuteSimilar)
        }
        if (!article.author.isNullOrBlank()) {
            NotifyAuthorMenuItem(onDismissRequest, onNotifyAuthor)
        }
        AutomationHistoryMenuItem(onDismissRequest, onShowAutomationHistory)
        if (unreadCount > 0) {
            if (index > 0) {
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.ArrowUpward,
                            contentDescription = null
                        )
                    },
                    text = { Text(stringResource(R.string.article_actions_mark_after_as_read)) },
                    onClick = { onMarkAllRead(After(article.id)) },
                )
            }
            DropdownMenuItem(
                leadingIcon = {
                    Icon(
                        Icons.Rounded.ArrowDownward,
                        contentDescription = null
                    )
                },
                text = { Text(stringResource(R.string.article_actions_mark_below_as_read)) },
                onClick = { onMarkAllRead(Before(article.id)) },
            )
        }
        CopyLinkMenuItem(onDismissRequest, article)
        ShareLinkMenuItem(onDismissRequest, article)
    }
}

@Composable
internal fun WallabagFailureMenuItem(
    record: ArticleIntegrationExportRecord,
) {
    val message = record.errorMessage
        ?.takeIf(::isSafeWallabagError)
        ?: stringResource(R.string.article_actions_wallabag_failed)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clearAndSetSemantics {
                contentDescription = message
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(
            Icons.Rounded.ErrorOutline,
            contentDescription = null,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
internal fun WallabagMenuItem(
    onDismissRequest: () -> Unit,
    record: ArticleIntegrationExportRecord?,
    onExportWallabag: () -> Unit,
) {
    val state = record?.state
    val icon = when (state) {
        ArticleIntegrationExportState.EXPORTED -> Icons.Rounded.CheckCircle
        ArticleIntegrationExportState.FAILED -> Icons.Rounded.Refresh
        else -> Icons.Rounded.CloudUpload
    }
    val label = when (state) {
        ArticleIntegrationExportState.QUEUED,
        ArticleIntegrationExportState.EXPORTING,
            -> R.string.article_actions_wallabag_queued

        ArticleIntegrationExportState.EXPORTED -> R.string.article_actions_wallabag_exported
        ArticleIntegrationExportState.FAILED -> R.string.article_actions_wallabag_retry
        null -> R.string.article_actions_wallabag_save
    }
    val enabled = state != ArticleIntegrationExportState.QUEUED &&
        state != ArticleIntegrationExportState.EXPORTING &&
        state != ArticleIntegrationExportState.EXPORTED

    DropdownMenuItem(
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
            )
        },
        text = { Text(stringResource(label)) },
        enabled = enabled,
        onClick = {
            onDismissRequest()
            onExportWallabag()
        },
    )
}

private fun isSafeWallabagError(message: String): Boolean {
    return message == "Wallabag authentication failed" ||
        message == "Wallabag is not configured" ||
        message == "Wallabag connection failed" ||
        message == "Wallabag export failed" ||
        WALLABAG_HTTP_ERROR.matches(message)
}

private val WALLABAG_HTTP_ERROR = Regex(
    """Wallabag request failed \(HTTP \d{3}\)"""
)

@Composable
internal fun OfflineFailureMenuItem(
    onDismissRequest: () -> Unit,
    offlinePackageRecord: ArticleOfflinePackageRecord,
) {
    val message = offlinePackageRecord.errorMessage
        ?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.article_actions_offline_failed_unknown)

    Box(
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = message
        }
    ) {
        DropdownMenuItem(
            leadingIcon = {
                Icon(
                    Icons.Rounded.ErrorOutline,
                    contentDescription = null
                )
            },
            text = { Text(message) },
            onClick = { onDismissRequest() },
        )
    }
}

@Composable
internal fun RetryOfflineMenuItem(
    onDismissRequest: () -> Unit,
    onRetryOffline: () -> Unit,
) {
    DropdownMenuItem(
        leadingIcon = {
            Icon(
                Icons.Rounded.Refresh,
                contentDescription = null
            )
        },
        text = { Text(stringResource(R.string.article_actions_retry_offline)) },
        onClick = {
            onDismissRequest()
            onRetryOffline()
        },
    )
}

@Composable
private fun AutomationHistoryMenuItem(
    onDismissRequest: () -> Unit,
    onShowAutomationHistory: () -> Unit,
) {
    DropdownMenuItem(
        leadingIcon = {
            Icon(
                Icons.Rounded.History,
                contentDescription = null
            )
        },
        text = { Text(stringResource(R.string.article_actions_automation_history)) },
        onClick = {
            onDismissRequest()
            onShowAutomationHistory()
        },
    )
}

@Composable
private fun DownloadOfflineMenuItem(
    onDismissRequest: () -> Unit,
    onDownloadOffline: () -> Unit,
) {
    DropdownMenuItem(
        leadingIcon = {
            Icon(
                Icons.Rounded.Download,
                contentDescription = null
            )
        },
        text = { Text(stringResource(R.string.article_actions_download_offline)) },
        onClick = {
            onDismissRequest()
            onDownloadOffline()
        },
    )
}

@Composable
private fun RemoveOfflineMenuItem(
    onDismissRequest: () -> Unit,
    onRemoveOffline: () -> Unit,
) {
    DropdownMenuItem(
        leadingIcon = {
            Icon(
                Icons.Rounded.Delete,
                contentDescription = null
            )
        },
        text = { Text(stringResource(R.string.article_actions_remove_offline)) },
        onClick = {
            onDismissRequest()
            onRemoveOffline()
        },
    )
}

@Composable
private fun MuteFeedMenuItem(
    onDismissRequest: () -> Unit,
    onMuteFeed: () -> Unit,
) {
    DropdownMenuItem(
        leadingIcon = {
            Icon(
                Icons.Rounded.Block,
                contentDescription = null
            )
        },
        text = { Text(stringResource(R.string.article_actions_mute_feed_rule)) },
        onClick = {
            onDismissRequest()
            onMuteFeed()
        },
    )
}

@Composable
private fun MuteSimilarMenuItem(
    onDismissRequest: () -> Unit,
    onMuteSimilar: () -> Unit,
) {
    DropdownMenuItem(
        leadingIcon = {
            Icon(
                Icons.Rounded.Block,
                contentDescription = null
            )
        },
        text = { Text(stringResource(R.string.article_actions_mute_similar_rule)) },
        onClick = {
            onDismissRequest()
            onMuteSimilar()
        },
    )
}

@Composable
private fun NotifyAuthorMenuItem(
    onDismissRequest: () -> Unit,
    onNotifyAuthor: () -> Unit,
) {
    DropdownMenuItem(
        leadingIcon = {
            Icon(
                Icons.Rounded.Notifications,
                contentDescription = null
            )
        },
        text = { Text(stringResource(R.string.article_actions_notify_author_rule)) },
        onClick = {
            onDismissRequest()
            onNotifyAuthor()
        },
    )
}

@Composable
private fun LabelMenuItem(
    onDismissRequest: () -> Unit,
    onOpenLabels: () -> Unit,
) {
    DropdownMenuItem(
        leadingIcon = {
            Icon(
                Icons.AutoMirrored.Outlined.Label,
                contentDescription = null
            )
        },
        text = { Text(stringResource(R.string.freshrss_article_actions_label)) },
        onClick = {
            onDismissRequest()
            onOpenLabels()
        },
    )
}

@Composable
private fun SaveForLaterMenuItem(
    onDismissRequest: () -> Unit,
    article: Article,
    onSaveForLater: (url: String) -> Unit,
) {
    val url = article.url?.toString() ?: return

    DropdownMenuItem(
        leadingIcon = {
            Icon(
                Icons.Outlined.BookmarkBorder,
                contentDescription = null
            )
        },
        text = { Text(stringResource(R.string.article_actions_save_for_later)) },
        onClick = {
            onDismissRequest()
            onSaveForLater(url)
        },
    )
}

@Composable
private fun CopyLinkMenuItem(onDismissRequest: () -> Unit, article: Article) {
    val url = article.url?.toString() ?: return

    val copyToClipboard = buildCopyToClipboard(url)

    DropdownMenuItem(
        leadingIcon = {
            Icon(
                Icons.Rounded.ContentCopy,
                contentDescription = null
            )
        },
        text = { Text(stringResource(R.string.article_actions_copy_link)) },
        onClick = {
            copyToClipboard()
            onDismissRequest()
        },
    )
}

@Composable
private fun ShareLinkMenuItem(onDismissRequest: () -> Unit, article: Article) {
    val url = article.url?.toString() ?: return

    val context = LocalContext.current

    val shareLink = {
        context.shareLink(url = url, article.title)
    }

    DropdownMenuItem(
        leadingIcon = {
            Icon(
                Icons.Rounded.Share,
                contentDescription = null
            )
        },
        text = { Text(stringResource(R.string.article_share)) },
        onClick = {
            shareLink()
            onDismissRequest()
        },
    )
}

@Composable
private fun ToggleReadMenuItem(onDismissRequest: () -> Unit, article: Article) {
    val action = readAction(article, LocalArticleActions.current)

    ToggleActionMenuItem(onDismissRequest, action)
}

@Composable
private fun ToggleStarMenuItem(onDismissRequest: () -> Unit, article: Article) {
    val action = starAction(article, LocalArticleActions.current)

    ToggleActionMenuItem(onDismissRequest, action)
}

@Composable
private fun ToggleActionMenuItem(
    onDismissRequest: () -> Unit,
    action: ArticleAction,
) {
    DropdownMenuItem(
        leadingIcon = {
            Icon(
                action.icon,
                contentDescription = null
            )
        },
        text = { Text(stringResource(action.translationKey)) },
        onClick = {
            onDismissRequest()
            action.commit()
        },
    )
}

@Preview(heightDp = 400, widthDp = 400)
@Composable
private fun ArticleActionMenuPreview(@PreviewParameter(ArticleSample::class) article: Article) {
    ArticleActionMenu(
        expanded = true,
        index = 0,
        article = article
    )
}
