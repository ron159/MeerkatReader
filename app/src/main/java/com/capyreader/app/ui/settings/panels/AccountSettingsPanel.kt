package com.capyreader.app.ui.settings.panels

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.capyreader.app.R
import com.capyreader.app.common.GetOPMLContent
import com.capyreader.app.common.RowItem
import com.capyreader.app.common.titleKey
import com.capyreader.app.preferences.AppTheme
import com.capyreader.app.transfers.BackupRestorePreview
import com.capyreader.app.transfers.BackupRestoreMode
import com.capyreader.app.transfers.CapyBackupFile
import com.capyreader.app.transfers.OPMLExporter
import com.capyreader.app.transfers.StarredExporter
import com.capyreader.app.ui.components.FormSection
import com.capyreader.app.ui.components.TextSwitch
import com.capyreader.app.ui.settings.AccountSettingsStrings
import com.capyreader.app.ui.theme.CapyTheme
import com.jocmp.capy.accounts.Source
import com.jocmp.capy.opml.ImportProgress
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun AccountSettingsPanel(
    onRemoveAccount: () -> Unit,
    viewModel: AccountSettingsViewModel = koinViewModel()
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val lastRefreshedAt by viewModel.lastRefreshedAt.collectAsState()
    val lastAutomaticBackupAt by viewModel.lastAutomaticBackupAt.collectAsState()
    val lastAutomaticBackupError by viewModel.lastAutomaticBackupError.collectAsState()

    val importer = rememberLauncherForActivityResult(
        GetOPMLContent()
    ) { uri ->
        viewModel.startOPMLImport(uri = uri)
    }

    val backupImporter = rememberLauncherForActivityResult(
        GetOPMLContent()
    ) { uri ->
        viewModel.prepareBackupImport(uri = uri)
    }

    val automaticBackupTreePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        viewModel.configureAutomaticBackup(uri)
    }

    val opmlExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/xml")
    ) { uri ->
        coroutineScope.launch {
            OPMLExporter(context).export(viewModel.account, target = uri)
        }
    }

    val backupExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        coroutineScope.launch {
            CapyBackupFile(context).export(viewModel.account, target = uri)
        }
    }

    val starredExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/html")
    ) { uri ->
        coroutineScope.launch {
            StarredExporter(context).export(viewModel.account, target = uri)
        }
    }

    AccountSettingsPanelView(
        onRequestRemoveAccount = {
            viewModel.removeAccount()
            onRemoveAccount()
        },
        onRequestImport = {
            importer.launch(listOf("text/xml", "text/x-opml", "application/*"))
        },
        onRequestBackupImport = {
            backupImporter.launch(listOf("application/json", "text/*", "application/octet-stream"))
        },
        onRequestExport = {
            opmlExporter.launch(OPMLExporter.DEFAULT_FILE_NAME)
        },
        onRequestBackupExport = {
            backupExporter.launch(CapyBackupFile.DEFAULT_FILE_NAME)
        },
        onRequestAutomaticBackupTree = {
            automaticBackupTreePicker.launch(null)
        },
        automaticBackupEnabled = viewModel.automaticBackupEnabled,
        updateAutomaticBackupEnabled = viewModel::updateAutomaticBackupEnabled,
        automaticBackupTreeUri = viewModel.automaticBackupTreeUri,
        automaticBackupRetention = viewModel.automaticBackupRetention,
        updateAutomaticBackupRetention = viewModel::updateAutomaticBackupRetention,
        onBackupNow = viewModel::backupNow,
        lastAutomaticBackupAt = lastAutomaticBackupAt,
        lastAutomaticBackupError = lastAutomaticBackupError,
        onRequestStarredExport = {
            starredExporter.launch(StarredExporter.DEFAULT_FILE_NAME)
        },
        importProgress = viewModel.importProgress,
        backupImportInProgress = viewModel.backupImportInProgress,
        backupRestorePreview = viewModel.backupRestorePreview,
        onCancelBackupImport = viewModel::cancelBackupImport,
        onConfirmBackupImport = viewModel::confirmBackupImport,
        accountSource = viewModel.accountSource,
        accountURL = viewModel.accountURL,
        accountName = viewModel.accountName,
        lastRefreshedAt = lastRefreshedAt,
    )
}

@Composable
fun AccountSettingsPanelView(
    onRequestRemoveAccount: () -> Unit,
    onRequestImport: () -> Unit,
    onRequestBackupImport: () -> Unit,
    onRequestExport: () -> Unit,
    onRequestBackupExport: () -> Unit,
    onRequestAutomaticBackupTree: () -> Unit,
    onRequestStarredExport: () -> Unit,
    automaticBackupEnabled: Boolean,
    updateAutomaticBackupEnabled: (Boolean) -> Unit,
    automaticBackupTreeUri: String,
    automaticBackupRetention: String,
    updateAutomaticBackupRetention: (String) -> Unit,
    onBackupNow: () -> Unit,
    lastAutomaticBackupAt: Long,
    lastAutomaticBackupError: String,
    accountSource: Source,
    accountURL: String,
    accountName: String,
    lastRefreshedAt: LastRefreshed,
    importProgress: ImportProgress?,
    backupImportInProgress: Boolean,
    backupRestorePreview: BackupRestorePreview?,
    onCancelBackupImport: () -> Unit,
    onConfirmBackupImport: (BackupRestoreMode) -> Unit,
) {
    val strings = AccountSettingsStrings.build(accountSource)
    val (isRemoveDialogOpen, setRemoveDialogOpen) = remember { mutableStateOf(false) }

    val onRequestRemove = {
        setRemoveDialogOpen(false)
        onRequestRemoveAccount()
    }

    val onRemoveCancel = {
        setRemoveDialogOpen(false)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.verticalScroll(rememberScrollState()),
    ) {
        if (showAccountName(accountSource)) {
            FormSection(
                title = stringResource(accountSource.titleKey),
            ) {
                RowItem {
                    Text(text = accountName)
                }
            }
            if (accountURL.isNotBlank()) {
                FormSection(
                    title = stringResource(R.string.settings_section_account_server),
                ) {
                    RowItem {
                        Text(
                            text = accountURL,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        FormSection(
            title = stringResource(R.string.settings_section_refresh),
        ) {
            RowItem {
                Text(
                    text = lastRefreshed(lastRefreshedAt),
                )
            }
        }

        FormSection(title = stringResource(R.string.settings_section_import)) {
            if (showImportButton(accountSource)) {
                RowItem {
                    OPMLImportButton(
                        onClick = {
                            onRequestImport()
                        },
                        importProgress = importProgress
                    )
                }
            }
            RowItem {
                BackupImportButton(
                    onClick = onRequestBackupImport,
                    inProgress = backupImportInProgress,
                )
            }
        }

        FormSection(title = stringResource(R.string.settings_section_export)) {
            RowItem {
                OPMLExportButton(
                    onClick = onRequestExport,
                )
            }
            RowItem {
                StarredExportButton(
                    onClick = onRequestStarredExport,
                )
            }
            RowItem {
                BackupExportButton(
                    onClick = onRequestBackupExport,
                )
            }
        }

        FormSection(title = stringResource(R.string.settings_section_automatic_backup)) {
            RowItem {
                FilledTonalButton(
                    onClick = onRequestAutomaticBackupTree,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (automaticBackupTreeUri.isBlank()) {
                                R.string.automatic_backup_choose_folder
                            } else {
                                R.string.automatic_backup_change_folder
                            }
                        )
                    )
                }
            }
            RowItem {
                TextSwitch(
                    checked = automaticBackupEnabled,
                    onCheckedChange = updateAutomaticBackupEnabled,
                    enabled = automaticBackupTreeUri.isNotBlank(),
                    title = stringResource(R.string.automatic_backup_enabled),
                    subtitle = automaticBackupTreeUri.takeIf(String::isNotBlank),
                )
            }
            RowItem {
                OutlinedTextField(
                    value = automaticBackupRetention,
                    onValueChange = updateAutomaticBackupRetention,
                    enabled = automaticBackupTreeUri.isNotBlank(),
                    label = { Text(stringResource(R.string.automatic_backup_retention)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            RowItem {
                FilledTonalButton(
                    onClick = onBackupNow,
                    enabled = automaticBackupEnabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.automatic_backup_now))
                }
            }
            RowItem {
                Text(
                    if (lastAutomaticBackupAt > 0) {
                        stringResource(
                            R.string.automatic_backup_last_success,
                            automaticBackupTime(lastAutomaticBackupAt),
                        )
                    } else {
                        stringResource(R.string.automatic_backup_never)
                    },
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                )
            }
            if (lastAutomaticBackupError.isNotBlank()) {
                RowItem {
                    Text(
                        stringResource(
                            R.string.automatic_backup_last_error,
                            lastAutomaticBackupError,
                        ),
                        color = colorScheme.error,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        FormSection {
            RowItem {
                HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                RemoveAccountButton(
                    source = accountSource,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { setRemoveDialogOpen(true) },
                ) {
                    Text(stringResource(strings.requestRemoveText))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (isRemoveDialogOpen) {
        AlertDialog(
            onDismissRequest = onRemoveCancel,
            title = { Text(stringResource(strings.dialogTitle)) },
            text = { Text(stringResource(strings.dialogMessage)) },
            dismissButton = {
                TextButton(onClick = onRemoveCancel) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
            confirmButton = {
                TextButton(onClick = onRequestRemove) {
                    Text(text = stringResource(strings.dialogConfirmText))
                }
            }
        )
    }

    if (backupRestorePreview != null) {
        BackupRestorePreviewDialog(
            preview = backupRestorePreview,
            onDismiss = onCancelBackupImport,
            onConfirm = onConfirmBackupImport,
        )
    }
}

@Composable
private fun BackupRestorePreviewDialog(
    preview: BackupRestorePreview,
    onDismiss: () -> Unit,
    onConfirm: (BackupRestoreMode) -> Unit,
) {
    val modes = BackupRestoreMode.entries
    val (selectedMode, setSelectedMode) = remember {
        mutableStateOf(BackupRestoreMode.REPLACE)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_restore_preview_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.backup_restore_preview_message))
                Text(stringResource(R.string.backup_restore_preview_version, preview.version))
                Text(stringResource(R.string.backup_restore_preview_source, preview.source.value))
                Text(
                    stringResource(
                        R.string.backup_restore_preview_subscriptions,
                        yesNo(preview.hasSubscriptions),
                    )
                )
                Text(
                    stringResource(
                        R.string.backup_restore_preview_saved_searches,
                        preview.savedSearchCount,
                    )
                )
                Text(stringResource(R.string.backup_restore_preview_read_later, preview.readLaterCount))
                Text(stringResource(R.string.backup_restore_preview_starred, preview.starredCount))
                Text(stringResource(R.string.backup_restore_preview_rules, yesNo(preview.hasRules)))
                Text(stringResource(R.string.backup_restore_preview_ai, yesNo(preview.hasAiSettings)))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = mode == selectedMode,
                            onClick = { setSelectedMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = modes.size,
                            ),
                        ) {
                            Text(
                                stringResource(
                                    when (mode) {
                                        BackupRestoreMode.REPLACE ->
                                            R.string.backup_restore_mode_replace

                                        BackupRestoreMode.MERGE ->
                                            R.string.backup_restore_mode_merge
                                    }
                                )
                            )
                        }
                    }
                }
                Text(
                    stringResource(
                        when (selectedMode) {
                            BackupRestoreMode.REPLACE ->
                                R.string.backup_restore_mode_replace_description

                            BackupRestoreMode.MERGE ->
                                R.string.backup_restore_mode_merge_description
                        }
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedMode) }) {
                Text(stringResource(R.string.backup_restore_preview_confirm))
            }
        }
    )
}

@Composable
private fun yesNo(value: Boolean): String {
    return stringResource(
        if (value) {
            R.string.backup_restore_preview_yes
        } else {
            R.string.backup_restore_preview_no
        }
    )
}

@Composable
fun RemoveAccountButton(
    source: Source,
    onClick: () -> Unit,
    modifier: Modifier,
    content: @Composable RowScope.() -> Unit
) {
    if (source == Source.LOCAL) {
        Button(
            colors = ButtonDefaults.buttonColors(
                containerColor = colorScheme.error,
                contentColor = colorScheme.contentColorFor(colorScheme.error)
            ), onClick = onClick, modifier = modifier, content = content
        )
    } else {
        FilledTonalButton(onClick = onClick, modifier = modifier, content = content)
    }
}

@Composable
fun BackupImportButton(
    onClick: () -> Unit,
    inProgress: Boolean,
) {
    Button(
        enabled = !inProgress,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            if (inProgress) {
                stringResource(R.string.backup_import_button_text_in_progress)
            } else {
                stringResource(R.string.backup_import_button_text)
            }
        )
    }
}

@Composable
fun BackupExportButton(
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.backup_export_button_text))
    }
}

fun showAccountName(source: Source): Boolean {
    return source != Source.LOCAL
}

fun showImportButton(source: Source): Boolean {
    return source == Source.LOCAL
}

private fun automaticBackupTime(epochSeconds: Long): String {
    return DateFormat.getDateTimeInstance()
        .format(Date(epochSeconds * 1_000))
}

@Composable
private fun lastRefreshed(lastRefreshed: LastRefreshed): String {
    return when (lastRefreshed) {
        is LastRefreshed.Never -> stringResource(R.string.settings_account_never_refreshed)
        is LastRefreshed.Today -> stringResource(R.string.settings_account_refresh_value_today, lastRefreshed.time)
        is LastRefreshed.Past -> stringResource(R.string.settings_account_refresh_value, lastRefreshed.date, lastRefreshed.time)
    }
}

@Preview
@Composable
private fun AccountSettingsPanelViewPreview() {
    CapyTheme(appTheme = AppTheme.NEWSPRINT) {
        AccountSettingsPanelView(
            onRequestRemoveAccount = {},
            onRequestImport = {},
            onRequestBackupImport = {},
            onRequestExport = {},
            onRequestBackupExport = {},
            onRequestAutomaticBackupTree = {},
            onRequestStarredExport = {},
            automaticBackupEnabled = false,
            updateAutomaticBackupEnabled = {},
            automaticBackupTreeUri = "",
            automaticBackupRetention = "7",
            updateAutomaticBackupRetention = {},
            onBackupNow = {},
            lastAutomaticBackupAt = 0,
            lastAutomaticBackupError = "",
            accountSource = Source.FEEDBIN,
            accountURL = "",
            accountName = "test@example.com",
            lastRefreshedAt = LastRefreshed.from(1700000000L),
            importProgress = null,
            backupImportInProgress = false,
            backupRestorePreview = null,
            onCancelBackupImport = {},
            onConfirmBackupImport = {},
        )
    }
}

@Preview
@Composable
private fun AccountSettingsPanelViewLocalPreview() {
    CapyTheme(appTheme = AppTheme.NEWSPRINT) {
        AccountSettingsPanelView(
            onRequestRemoveAccount = {},
            onRequestImport = {},
            onRequestBackupImport = {},
            onRequestExport = {},
            onRequestBackupExport = {},
            onRequestAutomaticBackupTree = {},
            onRequestStarredExport = {},
            automaticBackupEnabled = true,
            updateAutomaticBackupEnabled = {},
            automaticBackupTreeUri = "content://com.android.externalstorage.documents/tree/primary%3ADocuments",
            automaticBackupRetention = "7",
            updateAutomaticBackupRetention = {},
            onBackupNow = {},
            lastAutomaticBackupAt = 1_750_000_000,
            lastAutomaticBackupError = "",
            accountURL = "",
            accountSource = Source.LOCAL,
            accountName = "test@example.com",
            lastRefreshedAt = LastRefreshed.Never,
            importProgress = ImportProgress(currentCount = 3, total = 9001),
            backupImportInProgress = true,
            backupRestorePreview = BackupRestorePreview(
                version = 2,
                source = Source.LOCAL,
                hasSubscriptions = true,
                savedSearchCount = 2,
                readLaterCount = 0,
                starredCount = 12,
                hasRules = true,
                hasAiSettings = false,
            ),
            onCancelBackupImport = {},
            onConfirmBackupImport = {},
        )
    }
}
