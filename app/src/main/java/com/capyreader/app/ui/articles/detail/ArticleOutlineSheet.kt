package com.capyreader.app.ui.articles.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.capyreader.app.R

@Composable
internal fun ArticleOutlineSheet(
    items: List<ArticleOutlineItem>,
    onSelect: (ArticleOutlineItem) -> Unit,
) {
    val minimumLevel = items.minOf(ArticleOutlineItem::level)

    Column {
        Text(
            text = stringResource(R.string.article_outline_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp),
        ) {
            items(
                items = items,
                key = ArticleOutlineItem::targetID,
            ) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(item) }
                        .padding(
                            start = 24.dp + ((item.level - minimumLevel) * 16).dp,
                            end = 24.dp,
                            top = 12.dp,
                            bottom = 12.dp,
                        ),
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
