package com.capyreader.app.ui.settings.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.capyreader.app.R
import com.capyreader.app.common.RowItem
import com.capyreader.app.ui.components.FormSection
import com.capyreader.app.ui.components.TextSwitch
import org.koin.androidx.compose.koinViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun IntegrationSettingsPanel(
    viewModel: IntegrationSettingsViewModel = koinViewModel(),
) {
    val webDavLastBackupAt by viewModel.webDavLastBackupAt.collectAsState()
    val webDavLastError by viewModel.webDavLastError.collectAsState()

    IntegrationSettingsPanelView(
        wallabagEnabled = viewModel.wallabagEnabled,
        updateWallabagEnabled = viewModel::updateWallabagEnabled,
        wallabagServerUrl = viewModel.wallabagServerUrl,
        updateWallabagServerUrl = viewModel::updateWallabagServerUrl,
        wallabagAccessToken = viewModel.wallabagAccessToken,
        updateWallabagAccessToken = viewModel::updateWallabagAccessToken,
        wallabagLastError = viewModel.wallabagLastError,
        webDavBackupEnabled = viewModel.webDavBackupEnabled,
        updateWebDavBackupEnabled = viewModel::updateWebDavBackupEnabled,
        webDavDirectoryUrl = viewModel.webDavDirectoryUrl,
        updateWebDavDirectoryUrl = viewModel::updateWebDavDirectoryUrl,
        webDavUsername = viewModel.webDavUsername,
        updateWebDavUsername = viewModel::updateWebDavUsername,
        webDavPassword = viewModel.webDavPassword,
        updateWebDavPassword = viewModel::updateWebDavPassword,
        webDavConnectionTestState = viewModel.webDavConnectionTestState,
        webDavConnectionTestError = viewModel.webDavConnectionTestError,
        testWebDavConnection = viewModel::testWebDavConnection,
        backUpToWebDavNow = viewModel::backUpToWebDavNow,
        webDavLastBackupAt = webDavLastBackupAt,
        webDavLastError = webDavLastError,
    )
}

@Composable
internal fun IntegrationSettingsPanelView(
    wallabagEnabled: Boolean,
    updateWallabagEnabled: (Boolean) -> Unit,
    wallabagServerUrl: String,
    updateWallabagServerUrl: (String) -> Unit,
    wallabagAccessToken: String,
    updateWallabagAccessToken: (String) -> Unit,
    wallabagLastError: String,
    webDavBackupEnabled: Boolean,
    updateWebDavBackupEnabled: (Boolean) -> Unit,
    webDavDirectoryUrl: String,
    updateWebDavDirectoryUrl: (String) -> Unit,
    webDavUsername: String,
    updateWebDavUsername: (String) -> Unit,
    webDavPassword: String,
    updateWebDavPassword: (String) -> Unit,
    webDavConnectionTestState: WebDavConnectionTestState,
    webDavConnectionTestError: String,
    testWebDavConnection: () -> Unit,
    backUpToWebDavNow: () -> Unit,
    webDavLastBackupAt: Long,
    webDavLastError: String,
) {
    val webDavConnectionReady = webDavDirectoryUrl.isNotBlank() &&
        webDavUsername.isNotBlank() &&
        webDavPassword.isNotBlank()

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.verticalScroll(rememberScrollState()),
    ) {
        FormSection(title = stringResource(R.string.integration_wallabag_title)) {
            RowItem {
                TextSwitch(
                    checked = wallabagEnabled,
                    onCheckedChange = updateWallabagEnabled,
                    title = stringResource(R.string.integration_wallabag_enabled),
                    subtitle = stringResource(R.string.integration_wallabag_enabled_detail),
                )
            }

            RowItem {
                OutlinedTextField(
                    value = wallabagServerUrl,
                    onValueChange = updateWallabagServerUrl,
                    label = {
                        Text(
                            stringResource(R.string.integration_wallabag_server_url)
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            RowItem {
                OutlinedTextField(
                    value = wallabagAccessToken,
                    onValueChange = updateWallabagAccessToken,
                    label = {
                        Text(
                            stringResource(R.string.integration_wallabag_access_token)
                        )
                    },
                    supportingText = {
                        Text(
                            stringResource(R.string.integration_wallabag_token_privacy)
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (wallabagLastError.isNotBlank()) {
                RowItem {
                    Text(
                        text = stringResource(
                            R.string.integration_wallabag_last_error,
                            wallabagLastError,
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        FormSection(title = stringResource(R.string.integration_webdav_backup_title)) {
            RowItem {
                TextSwitch(
                    checked = webDavBackupEnabled,
                    onCheckedChange = updateWebDavBackupEnabled,
                    title = stringResource(R.string.integration_webdav_backup_enabled),
                    subtitle = stringResource(
                        R.string.integration_webdav_backup_enabled_detail
                    ),
                )
            }

            RowItem {
                OutlinedTextField(
                    value = webDavDirectoryUrl,
                    onValueChange = updateWebDavDirectoryUrl,
                    label = {
                        Text(
                            stringResource(R.string.integration_webdav_directory_url)
                        )
                    },
                    supportingText = {
                        Text(
                            stringResource(R.string.integration_webdav_nextcloud_hint)
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            RowItem {
                OutlinedTextField(
                    value = webDavUsername,
                    onValueChange = updateWebDavUsername,
                    label = {
                        Text(stringResource(R.string.integration_webdav_username))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            RowItem {
                OutlinedTextField(
                    value = webDavPassword,
                    onValueChange = updateWebDavPassword,
                    label = {
                        Text(stringResource(R.string.integration_webdav_app_password))
                    },
                    supportingText = {
                        Text(
                            stringResource(R.string.integration_webdav_password_privacy)
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            RowItem {
                FilledTonalButton(
                    onClick = testWebDavConnection,
                    enabled = webDavConnectionReady &&
                        webDavConnectionTestState !=
                        WebDavConnectionTestState.TESTING,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (webDavConnectionTestState ==
                                WebDavConnectionTestState.TESTING
                            ) {
                                R.string.integration_webdav_testing
                            } else {
                                R.string.integration_webdav_test_connection
                            }
                        )
                    )
                }
            }

            when (webDavConnectionTestState) {
                WebDavConnectionTestState.SUCCESS -> {
                    RowItem {
                        Text(
                            text = stringResource(
                                R.string.integration_webdav_connection_success
                            ),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                WebDavConnectionTestState.FAILED -> {
                    RowItem {
                        Text(
                            text = stringResource(
                                R.string.integration_webdav_connection_failed,
                                webDavConnectionTestError,
                            ),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                WebDavConnectionTestState.IDLE,
                WebDavConnectionTestState.TESTING -> Unit
            }

            RowItem {
                FilledTonalButton(
                    onClick = backUpToWebDavNow,
                    enabled = webDavBackupEnabled && webDavConnectionReady,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.integration_webdav_backup_now))
                }
            }

            RowItem {
                Text(
                    if (webDavLastBackupAt > 0) {
                        stringResource(
                            R.string.integration_webdav_last_success,
                            webDavBackupTime(webDavLastBackupAt),
                        )
                    } else {
                        stringResource(R.string.integration_webdav_never)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (webDavLastError.isNotBlank()) {
                RowItem {
                    Text(
                        text = stringResource(
                            R.string.integration_webdav_last_error,
                            webDavLastError,
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

private fun webDavBackupTime(epochSeconds: Long): String {
    return DateFormat.getDateTimeInstance()
        .format(Date(epochSeconds * 1_000))
}
