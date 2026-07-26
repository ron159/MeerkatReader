package com.capyreader.app.integrations.webdav

import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.transfers.CapyBackupFile
import com.jocmp.capy.Account
import kotlinx.coroutines.CancellationException
import java.time.Instant

class WebDavBackupUploader(
    private val account: Account,
    private val appPreferences: AppPreferences,
    private val backupFile: CapyBackupFile,
    private val client: WebDavBackupClient,
) {
    suspend fun uploadNow(exportedAt: Instant = Instant.now()): Result<Unit> {
        val options = appPreferences.webDavBackupOptions
        if (!options.enabled.get()) {
            return Result.success(Unit)
        }

        if (!options.hasConnectionDetails()) {
            return recordFailure(WebDavConfigurationException())
        }

        return try {
            val payload = backupFile.createPayload(account, exportedAt)
            client.upload(
                fileName = CapyBackupFile.automaticBackupFileName(exportedAt),
                payload = payload,
            ).fold(
                onSuccess = {
                    options.lastBackupAt.set(exportedAt.epochSecond)
                    options.lastError.set("")
                    Result.success(Unit)
                },
                onFailure = ::recordFailure,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            recordFailure(error)
        }
    }

    private fun recordFailure(error: Throwable): Result<Unit> {
        appPreferences.webDavBackupOptions.lastError.set(
            webDavBackupErrorMessage(error)
        )
        return Result.failure(error)
    }
}
