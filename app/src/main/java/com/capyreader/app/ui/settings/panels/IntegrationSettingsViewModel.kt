package com.capyreader.app.ui.settings.panels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capyreader.app.integrations.webdav.WebDavBackupClient
import com.capyreader.app.integrations.webdav.WebDavBackupScheduler
import com.capyreader.app.integrations.webdav.stripWebDavUrlCredentials
import com.capyreader.app.integrations.webdav.webDavBackupErrorMessage
import com.capyreader.app.preferences.AppPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class IntegrationSettingsViewModel(
    private val appPreferences: AppPreferences,
    private val webDavBackupClient: WebDavBackupClient,
    private val webDavBackupScheduler: WebDavBackupScheduler,
) : ViewModel() {
    var wallabagEnabled by mutableStateOf(appPreferences.wallabagOptions.enabled.get())
        private set

    var wallabagServerUrl by mutableStateOf(appPreferences.wallabagOptions.serverUrl.get())
        private set

    var wallabagAccessToken by mutableStateOf(appPreferences.wallabagOptions.accessToken.get())
        private set

    var wallabagLastError by mutableStateOf(appPreferences.wallabagOptions.lastError.get())
        private set

    var webDavBackupEnabled by mutableStateOf(
        appPreferences.webDavBackupOptions.enabled.get()
    )
        private set

    var webDavDirectoryUrl by mutableStateOf(
        appPreferences.webDavBackupOptions.directoryUrl.get()
    )
        private set

    var webDavUsername by mutableStateOf(
        appPreferences.webDavBackupOptions.username.get()
    )
        private set

    var webDavPassword by mutableStateOf(
        appPreferences.webDavBackupOptions.password.get()
    )
        private set

    var webDavConnectionTestState by mutableStateOf(WebDavConnectionTestState.IDLE)
        private set

    var webDavConnectionTestError by mutableStateOf("")
        private set

    val webDavLastBackupAt = appPreferences.webDavBackupOptions.lastBackupAt
        .changes()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            appPreferences.webDavBackupOptions.lastBackupAt.get(),
        )

    val webDavLastError = appPreferences.webDavBackupOptions.lastError
        .changes()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            appPreferences.webDavBackupOptions.lastError.get(),
        )

    private var webDavConnectionTestJob: Job? = null

    fun updateWallabagEnabled(enabled: Boolean) {
        appPreferences.wallabagOptions.enabled.set(enabled)
        wallabagEnabled = enabled
    }

    fun updateWallabagServerUrl(serverUrl: String) {
        appPreferences.wallabagOptions.serverUrl.set(serverUrl)
        wallabagServerUrl = serverUrl
        clearWallabagError()
    }

    fun updateWallabagAccessToken(accessToken: String) {
        appPreferences.wallabagOptions.accessToken.set(accessToken)
        wallabagAccessToken = appPreferences.wallabagOptions.accessToken.get()
        clearWallabagError()
    }

    fun updateWebDavBackupEnabled(enabled: Boolean) {
        webDavBackupScheduler.setEnabled(enabled)
        webDavBackupEnabled = enabled
        resetWebDavConnectionTest()
    }

    fun updateWebDavDirectoryUrl(directoryUrl: String) {
        val safeDirectoryUrl = stripWebDavUrlCredentials(directoryUrl)
        appPreferences.webDavBackupOptions.directoryUrl.set(safeDirectoryUrl)
        webDavDirectoryUrl = safeDirectoryUrl
        webDavConfigurationChanged()
    }

    fun updateWebDavUsername(username: String) {
        appPreferences.webDavBackupOptions.username.set(username)
        webDavUsername = username
        webDavConfigurationChanged()
    }

    fun updateWebDavPassword(password: String) {
        appPreferences.webDavBackupOptions.password.set(password)
        webDavPassword = appPreferences.webDavBackupOptions.password.get()
        webDavConfigurationChanged()
    }

    fun testWebDavConnection() {
        webDavConnectionTestJob?.cancel()
        webDavConnectionTestState = WebDavConnectionTestState.TESTING
        webDavConnectionTestError = ""

        webDavConnectionTestJob = viewModelScope.launch {
            webDavBackupClient.testConnection().fold(
                onSuccess = {
                    webDavConnectionTestState = WebDavConnectionTestState.SUCCESS
                },
                onFailure = { error ->
                    webDavConnectionTestState = WebDavConnectionTestState.FAILED
                    webDavConnectionTestError = webDavBackupErrorMessage(error)
                },
            )
        }
    }

    fun backUpToWebDavNow() {
        webDavBackupScheduler.enqueueNow()
    }

    private fun webDavConfigurationChanged() {
        appPreferences.webDavBackupOptions.lastError.set("")
        webDavBackupScheduler.configurationChanged()
        resetWebDavConnectionTest()
    }

    private fun resetWebDavConnectionTest() {
        webDavConnectionTestJob?.cancel()
        webDavConnectionTestJob = null
        webDavConnectionTestState = WebDavConnectionTestState.IDLE
        webDavConnectionTestError = ""
    }

    private fun clearWallabagError() {
        appPreferences.wallabagOptions.lastError.set("")
        wallabagLastError = ""
    }
}

enum class WebDavConnectionTestState {
    IDLE,
    TESTING,
    SUCCESS,
    FAILED,
}
