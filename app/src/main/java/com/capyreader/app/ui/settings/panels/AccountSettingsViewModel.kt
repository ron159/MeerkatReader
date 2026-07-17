package com.capyreader.app.ui.settings.panels

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.R
import com.capyreader.app.common.toast
import com.capyreader.app.transfers.CapyBackupFile
import com.capyreader.app.transfers.BackupRestorePreview
import com.capyreader.app.transfers.BackupRestoreMode
import com.capyreader.app.transfers.AutomaticBackupScheduler
import com.capyreader.app.transfers.OPMLImportWorker
import com.capyreader.app.transfers.OPMLImportWorker.Companion.PROGRESS_CURRENT_COUNT
import com.capyreader.app.transfers.OPMLImportWorker.Companion.PROGRESS_TOTAL
import com.jocmp.capy.Account
import com.jocmp.capy.AccountManager
import com.jocmp.capy.accounts.Source
import com.jocmp.capy.opml.ImportProgress
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AccountSettingsViewModel(
    private val accountManager: AccountManager,
    val account: Account,
    private val appPreferences: AppPreferences,
    private val automaticBackupScheduler: AutomaticBackupScheduler,
    application: Application
) : AndroidViewModel(application) {
    val accountSource: Source = account.source

    var importProgress by mutableStateOf<ImportProgress?>(null)
        private set

    var backupImportInProgress by mutableStateOf(false)
        private set

    var backupRestorePreview by mutableStateOf<BackupRestorePreview?>(null)
        private set

    private var backupRestoreUri: Uri? = null

    var automaticBackupEnabled by mutableStateOf(appPreferences.automaticBackupEnabled.get())
        private set

    var automaticBackupTreeUri by mutableStateOf(appPreferences.automaticBackupTreeUri.get())
        private set

    var automaticBackupRetention by mutableStateOf(
        appPreferences.automaticBackupRetention.get().toString()
    )
        private set

    val lastAutomaticBackupAt = appPreferences.lastAutomaticBackupAt
        .changes()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            appPreferences.lastAutomaticBackupAt.get(),
        )

    val lastAutomaticBackupError = appPreferences.lastAutomaticBackupError
        .changes()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            appPreferences.lastAutomaticBackupError.get(),
        )

    val accountURL = account.preferences.url.get()

    val accountName = account.preferences.username.get()

    val lastRefreshedAt = account.preferences.lastRefreshedAt
        .changes()
        .map { LastRefreshed.from(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LastRefreshed.Never)

    fun removeAccount() {
        automaticBackupScheduler.clear()
        appPreferences.clearAll()
        accountManager.removeAccount(accountID = account.id)
    }

    fun configureAutomaticBackup(uri: Uri?) {
        uri ?: return

        automaticBackupScheduler.configure(uri)
            .onSuccess {
                automaticBackupTreeUri = uri.toString()
                automaticBackupEnabled = true
            }
            .onFailure {
                applicationContext.toast(R.string.automatic_backup_folder_error)
            }
    }

    fun updateAutomaticBackupEnabled(enabled: Boolean) {
        automaticBackupScheduler.setEnabled(enabled)
        automaticBackupEnabled = enabled && automaticBackupTreeUri.isNotBlank()
    }

    fun updateAutomaticBackupRetention(value: String) {
        val sanitized = value.filter(Char::isDigit).take(2)
        automaticBackupRetention = sanitized

        sanitized.toIntOrNull()?.let { retention ->
            val clamped = retention.coerceIn(1, 30)
            automaticBackupScheduler.setRetention(clamped)
            automaticBackupRetention = clamped.toString()
        }
    }

    fun backupNow() {
        automaticBackupScheduler.enqueueNow()
    }

    fun startOPMLImport(uri: Uri?) {
        uri ?: return

        val requestID = OPMLImportWorker.performAsync(applicationContext, uri)

        viewModelScope.launch {
            WorkManager.getInstance(applicationContext)
                .getWorkInfoByIdFlow(requestID)
                .collect { workInfo: WorkInfo? ->
                    if (workInfo == null || workInfo.state.isFinished) {
                        importProgress = null
                    } else {
                        val currentCount = workInfo.progress.getInt(PROGRESS_CURRENT_COUNT, 0)
                        val total = workInfo.progress.getInt(PROGRESS_TOTAL, 0)

                        importProgress = ImportProgress(
                            currentCount = currentCount,
                            total = total
                        )
                    }
                }
        }
    }

    fun prepareBackupImport(uri: Uri?) {
        uri ?: return

        viewModelScope.launch {
            val preview = CapyBackupFile(applicationContext).restorePreview(account, uri)

            if (preview != null) {
                backupRestoreUri = uri
                backupRestorePreview = preview
            }
        }
    }

    fun cancelBackupImport() {
        backupRestoreUri = null
        backupRestorePreview = null
    }

    fun confirmBackupImport(mode: BackupRestoreMode) {
        val uri = backupRestoreUri ?: return

        cancelBackupImport()
        startBackupImport(uri = uri, mode = mode)
    }

    private fun startBackupImport(
        uri: Uri?,
        mode: BackupRestoreMode,
    ) {
        uri ?: return

        viewModelScope.launch {
            backupImportInProgress = true

            try {
                CapyBackupFile(applicationContext).restore(account, uri, mode)
            } finally {
                backupImportInProgress = false
            }
        }
    }

    private val applicationContext: Context
        get() = getApplication<Application>().applicationContext
}
