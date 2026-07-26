package com.capyreader.app.ui.articles.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.capyreader.app.R
import com.capyreader.app.ui.components.DialogCard
import com.capyreader.app.ui.components.buildCopyToClipboard

@Composable
fun CopyTextDialog(
    text: String,
    onClose: () -> Unit,
) {
    val copy = buildCopyToClipboard(text)
    val listItemColors =
        ListItemDefaults.colors(containerColor = CardDefaults.cardColors().containerColor)

    Dialog(onDismissRequest = onClose) {
        DialogCard {
            Column(Modifier.fillMaxWidth()) {
                SelectionContainer {
                    Text(
                        text = text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    )
                }
                HorizontalDivider()
                ListItem(
                    modifier = Modifier.clickable {
                        copy()
                        onClose()
                    },
                    colors = listItemColors,
                    headlineContent = {
                        Text(stringResource(R.string.actions_copy_text))
                    },
                )
            }
        }
    }
}
