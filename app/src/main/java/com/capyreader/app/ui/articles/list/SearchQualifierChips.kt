package com.capyreader.app.ui.articles.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.capyreader.app.R
import com.jocmp.capy.ArticleSearchQuery
import com.jocmp.capy.ArticleSearchStatus
import java.time.Instant
import java.time.ZoneOffset

@Composable
fun SearchQualifierChips(
    query: String,
    modifier: Modifier = Modifier,
) {
    val chips = qualifierChips(query)

    if (chips.isEmpty()) return

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        items(chips, key = { it }) { chip ->
            AssistChip(
                onClick = {},
                label = { Text(chip) },
            )
        }
    }
}

@Composable
private fun qualifierChips(queryText: String): List<String> {
    val query = ArticleSearchQuery.parse(queryText)
    val chips = mutableListOf<String>()

    query.status?.let {
        chips += stringResource(R.string.search_chip_status, it.label())
    }
    query.feed?.let {
        chips += stringResource(R.string.search_chip_feed, it)
    }
    query.folder?.let {
        chips += stringResource(R.string.search_chip_folder, it)
    }
    query.author?.let {
        chips += stringResource(R.string.search_chip_author, it)
    }
    query.title?.let {
        chips += stringResource(R.string.search_chip_title, it)
    }
    query.afterEpochSeconds?.let {
        chips += stringResource(R.string.search_chip_after, it.toDateLabel())
    }
    query.beforeEpochSeconds?.let {
        chips += stringResource(R.string.search_chip_before, it.toDateLabel())
    }
    if (query.hasImage == true) {
        chips += stringResource(R.string.search_chip_has_image)
    }
    if (query.hasAudio == true) {
        chips += stringResource(R.string.search_chip_has_audio)
    }

    return chips
}

@Composable
private fun ArticleSearchStatus.label(): String {
    return when (this) {
        ArticleSearchStatus.READ -> stringResource(R.string.search_chip_status_read)
        ArticleSearchStatus.UNREAD -> stringResource(R.string.search_chip_status_unread)
        ArticleSearchStatus.STARRED -> stringResource(R.string.search_chip_status_starred)
        ArticleSearchStatus.SAVED -> stringResource(R.string.search_chip_status_saved)
    }
}

private fun Long.toDateLabel(): String {
    return Instant.ofEpochSecond(this)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .toString()
}
