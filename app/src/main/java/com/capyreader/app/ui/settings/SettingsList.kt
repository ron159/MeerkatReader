package com.capyreader.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults.pinnedScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.capyreader.app.R
import com.capyreader.app.ui.isCompact
import com.capyreader.app.ui.settings.panels.SettingsPanel
import com.capyreader.app.ui.theme.CapyTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsList(
    selected: SettingsPanel?,
    onNavigate: (panel: SettingsPanel) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val scrollBehavior = pinnedScrollBehavior()
    val compact = isCompact()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(stringResource(R.string.settings_list_top_bar_title))
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(vertical = 16.dp),
        ) {
            SettingsPanel.groups.forEach { group ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(group.title),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .semantics { heading() },
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth(),
                    ) {
                        Column {
                            group.panels.forEach { panel ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(MaterialTheme.shapes.medium)
                                        .clickable(role = Role.Button) { onNavigate(panel) }
                                ) {
                                    ListItem(
                                        leadingContent = {
                                            Icon(
                                                imageVector = panel.icon(),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        },
                                        colors = ListItemDefaults.colors(
                                            containerColor = if (!compact && panel == selected) {
                                                MaterialTheme.colorScheme.surfaceContainerHigh
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerLow
                                            }
                                        ),
                                        headlineContent = {
                                            Text(stringResource(panel.title))
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun SettingsListPreview() {
    CapyTheme {
        SettingsList(
            selected = SettingsPanel.Display,
            onNavigate = {},
            onNavigateBack = {}
        )
    }
}
