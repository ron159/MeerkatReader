package com.capyreader.app.transfers

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.capyreader.app.preferences.AppPreferences
import com.jocmp.capy.Account
import com.jocmp.capy.logging.CapyLog
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Instant

class AutomaticBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams), KoinComponent {
    private val account by inject<Account>()
    private val appPreferences by inject<AppPreferences>()
    private val backupFile by inject<CapyBackupFile>()

    override suspend fun doWork(): Result {
        if (!appPreferences.automaticBackupEnabled.get()) {
            return Result.success()
        }

        val treeUri = appPreferences.automaticBackupTreeUri.get()
            .takeIf(String::isNotBlank)
            ?.let(Uri::parse)
            ?: return Result.failure()

        return runCatching {
            backupFile.exportAutomatic(
                account = account,
                treeUri = treeUri,
                retention = appPreferences.automaticBackupRetention.get(),
            )
            appPreferences.lastAutomaticBackupAt.set(Instant.now().epochSecond)
            appPreferences.lastAutomaticBackupError.set("")
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { error ->
                CapyLog.error("automatic_backup", error)
                appPreferences.lastAutomaticBackupError.set(
                    error.message ?: error::class.simpleName.orEmpty()
                )
                if (error is SecurityException) {
                    Result.failure()
                } else {
                    Result.retry()
                }
            },
        )
    }
}
