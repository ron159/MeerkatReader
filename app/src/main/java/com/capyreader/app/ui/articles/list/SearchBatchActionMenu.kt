package com.capyreader.app.ui.articles.list

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.capyreader.app.R

@Composable
fun SearchBatchActionMenu(
    canSaveExternally: Boolean,
    onMarkRead: () -> Unit,
    onStar: () -> Unit,
    onSaveExternally: () -> Unit,
) {
    val (expanded, setExpanded) = remember { mutableStateOf(false) }

    IconButton(onClick = { setExpanded(true) }) {
        Icon(
            imageVector = Icons.Filled.MoreVert,
            contentDescription = stringResource(R.string.search_batch_actions),
        )
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { setExpanded(false) },
    ) {
        DropdownMenuItem(
            leadingIcon = {
                Icon(Icons.Rounded.DoneAll, contentDescription = null)
            },
            text = { Text(stringResource(R.string.search_batch_mark_read)) },
            onClick = {
                setExpanded(false)
                onMarkRead()
            },
        )
        DropdownMenuItem(
            leadingIcon = {
                Icon(Icons.Rounded.Star, contentDescription = null)
            },
            text = { Text(stringResource(R.string.search_batch_star)) },
            onClick = {
                setExpanded(false)
                onStar()
            },
        )
        if (canSaveExternally) {
            DropdownMenuItem(
                leadingIcon = {
                    Icon(Icons.Rounded.SaveAlt, contentDescription = null)
                },
                text = { Text(stringResource(R.string.search_batch_save_externally)) },
                onClick = {
                    setExpanded(false)
                    onSaveExternally()
                },
            )
        }
    }
}
