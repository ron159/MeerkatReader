package com.capyreader.app.ui.articles.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.capyreader.app.R
import com.capyreader.app.tts.ArticleTtsState
import com.capyreader.app.tts.ArticleTtsStatus
import com.capyreader.app.ui.theme.CapyTheme
import com.jocmp.capy.persistence.ArticleTtsSource

@Composable
fun ArticleTtsPlayer(
    state: ArticleTtsState,
    onPlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    availableSources: List<ArticleTtsSource> = listOf(ArticleTtsSource.ORIGINAL),
    onSelectSource: (ArticleTtsSource) -> Unit = {},
) {
    if (!state.isVisible) {
        return
    }

    val sources = orderedTtsSources(availableSources, state.source)
    var sourceMenuOpen by remember(state.articleID) { mutableStateOf(false) }

    Surface(
        tonalElevation = 6.dp,
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .zIndex(2f)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = ArticleBarDefaults.BottomBarHeight + 20.dp)
            .fillMaxWidth(),
    ) {
        BoxWithConstraints {
            if (maxWidth < COMPACT_PLAYER_WIDTH) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    ArticleTtsInfo(
                        state = state,
                        sources = sources,
                        sourceMenuOpen = sourceMenuOpen,
                        setSourceMenuOpen = { sourceMenuOpen = it },
                        onSelectSource = onSelectSource,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ArticleTtsControls(
                        state = state,
                        onPlayPause = onPlayPause,
                        onSkipPrevious = onSkipPrevious,
                        onSkipNext = onSkipNext,
                        onDismiss = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    ArticleTtsInfo(
                        state = state,
                        sources = sources,
                        sourceMenuOpen = sourceMenuOpen,
                        setSourceMenuOpen = { sourceMenuOpen = it },
                        onSelectSource = onSelectSource,
                        modifier = Modifier.weight(1f),
                    )
                    ArticleTtsControls(
                        state = state,
                        onPlayPause = onPlayPause,
                        onSkipPrevious = onSkipPrevious,
                        onSkipNext = onSkipNext,
                        onDismiss = onDismiss,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArticleTtsInfo(
    state: ArticleTtsState,
    sources: List<ArticleTtsSource>,
    sourceMenuOpen: Boolean,
    setSourceMenuOpen: (Boolean) -> Unit,
    onSelectSource: (ArticleTtsSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Icon(
            imageVector = Icons.Rounded.Headphones,
            contentDescription = null,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .size(28.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.articleTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    val sourceText = ttsSourceText(state.source)
                    val sourceModifier = if (sources.size > 1) {
                        Modifier.clickable(
                            onClickLabel = stringResource(R.string.article_tts_source),
                            role = Role.Button,
                        ) {
                            setSourceMenuOpen(true)
                        }
                    } else {
                        Modifier
                    }
                    Text(
                        text = sourceText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = sourceModifier,
                    )
                    DropdownMenu(
                        expanded = sourceMenuOpen,
                        onDismissRequest = { setSourceMenuOpen(false) },
                    ) {
                        sources.forEach { source ->
                            DropdownMenuItem(
                                text = { Text(ttsSourceText(source)) },
                                onClick = {
                                    setSourceMenuOpen(false)
                                    onSelectSource(source)
                                },
                            )
                        }
                    }
                }
                Text(
                    text = " · ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = ttsStatusText(state),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (
                        state.status == ArticleTtsStatus.ERROR ||
                        state.status == ArticleTtsStatus.UNAVAILABLE
                    ) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                )
            }
        }
    }
}

@Composable
private fun ArticleTtsControls(
    state: ArticleTtsState,
    onPlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        IconButton(
            enabled = state.sentenceCount > 0 && state.sentenceIndex > 0,
            onClick = onSkipPrevious,
        ) {
            Icon(
                Icons.Rounded.SkipPrevious,
                contentDescription = stringResource(R.string.article_tts_previous_sentence),
            )
        }
        IconButton(
            enabled = state.status != ArticleTtsStatus.INITIALIZING,
            onClick = onPlayPause,
        ) {
            Icon(
                if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(
                    if (state.isPlaying) {
                        R.string.article_tts_pause
                    } else {
                        R.string.article_tts_play
                    }
                ),
            )
        }
        IconButton(
            enabled = state.sentenceCount > 0 &&
                state.sentenceIndex < state.sentenceCount - 1,
            onClick = onSkipNext,
        ) {
            Icon(
                Icons.Rounded.SkipNext,
                contentDescription = stringResource(R.string.article_tts_next_sentence),
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(R.string.article_tts_close),
            )
        }
    }
}

@Composable
private fun ttsSourceText(source: ArticleTtsSource): String {
    return stringResource(
        when (source) {
            ArticleTtsSource.ORIGINAL -> R.string.article_tts_source_original
            ArticleTtsSource.AI_SUMMARY -> R.string.article_tts_source_ai_summary
            ArticleTtsSource.TRANSLATION -> R.string.article_tts_source_translation
        }
    )
}

internal fun orderedTtsSources(
    availableSources: List<ArticleTtsSource>,
    currentSource: ArticleTtsSource,
): List<ArticleTtsSource> {
    return (availableSources + currentSource)
        .distinct()
        .sortedBy(ArticleTtsSource::ordinal)
}

@Composable
internal fun ttsStatusText(state: ArticleTtsState): String {
    return when (state.status) {
        ArticleTtsStatus.IDLE -> ""
        ArticleTtsStatus.INITIALIZING -> stringResource(R.string.article_tts_initializing)
        ArticleTtsStatus.PLAYING,
        ArticleTtsStatus.PAUSED,
            -> stringResource(
                R.string.article_tts_sentence_progress,
                state.sentenceIndex + 1,
                state.sentenceCount,
            )

        ArticleTtsStatus.COMPLETED -> stringResource(R.string.article_tts_completed)
        ArticleTtsStatus.UNAVAILABLE,
        ArticleTtsStatus.ERROR,
            -> stringResource(R.string.article_tts_unavailable)
    }
}

private val COMPACT_PLAYER_WIDTH = 360.dp

@Preview(widthDp = 400)
@Composable
private fun ArticleTtsPlayerPreview() {
    CapyTheme {
        ArticleTtsPlayer(
            state = ArticleTtsState(
                articleID = "article-1",
                articleTitle = "A long article title that will be truncated",
                status = ArticleTtsStatus.PLAYING,
                sentenceIndex = 4,
                sentenceCount = 18,
            ),
            onPlayPause = {},
            onSkipPrevious = {},
            onSkipNext = {},
            onDismiss = {},
        )
    }
}
