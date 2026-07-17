package com.capyreader.app.transfers

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.capyreader.app.preferences.AppPreferences
import java.util.concurrent.TimeUnit

class AutomaticBackupScheduler(
    private val context: Context,
    private val appPreferences: AppPreferences,
) {
    private var observedAccountPreferences: SharedPreferences? = null
    private var listenersRegistered = false

    private val preferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key !in IGNORED_PREFERENCE_KEYS) {
                enqueueAfterChange()
            }
        }

    fun initialize() {
        registerPreferenceListeners()
        updatePeriodicWork()
    }

    fun configure(treeUri: Uri): Result<Unit> = runCatching {
        val resolver = context.contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        resolver.takePersistableUriPermission(treeUri, flags)

        val oldUri = appPreferences.automaticBackupTreeUri.get()
            .takeIf(String::isNotBlank)
            ?.let(Uri::parse)
        if (oldUri != null && oldUri != treeUri) {
            runCatching { resolver.releasePersistableUriPermission(oldUri, flags) }
        }

        appPreferences.automaticBackupTreeUri.set(treeUri.toString())
        appPreferences.automaticBackupEnabled.set(true)
        appPreferences.lastAutomaticBackupError.set("")
        updatePeriodicWork()
        enqueueNow()
    }

    fun setEnabled(enabled: Boolean) {
        val hasTree = appPreferences.automaticBackupTreeUri.get().isNotBlank()
        appPreferences.automaticBackupEnabled.set(enabled && hasTree)
        updatePeriodicWork()
        if (enabled && hasTree) {
            enqueueNow()
        }
    }

    fun setRetention(retention: Int) {
        appPreferences.automaticBackupRetention.set(retention.coerceIn(1, 30))
        updatePeriodicWork()
    }

    fun enqueueNow() {
        if (!isConfigured()) {
            return
        }

        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(CHANGE_WORK_NAME)

        val request = OneTimeWorkRequestBuilder<AutomaticBackupWorker>()
            .setConstraints(automaticBackupConstraints())
            .build()

        workManager.enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun enqueueAfterChange() {
        if (!isConfigured()) {
            return
        }

        val request = OneTimeWorkRequestBuilder<AutomaticBackupWorker>()
            .setInitialDelay(CHANGE_DEBOUNCE_MINUTES, TimeUnit.MINUTES)
            .setConstraints(automaticBackupConstraints())
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            CHANGE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun clear() {
        unregisterPreferenceListeners()
        cancelAll()

        val treeUri = appPreferences.automaticBackupTreeUri.get()
            .takeIf(String::isNotBlank)
            ?.let(Uri::parse)
        if (treeUri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                context.contentResolver.releasePersistableUriPermission(treeUri, flags)
            }
        }

        appPreferences.automaticBackupEnabled.set(false)
        appPreferences.automaticBackupTreeUri.set("")
        appPreferences.lastAutomaticBackupAt.set(0L)
        appPreferences.lastAutomaticBackupError.set("")
    }

    private fun updatePeriodicWork() {
        val workManager = WorkManager.getInstance(context)
        if (!isConfigured()) {
            workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
            return
        }

        val request = PeriodicWorkRequestBuilder<AutomaticBackupWorker>(
            1,
            TimeUnit.DAYS,
        )
            .setConstraints(automaticBackupConstraints())
            .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private fun isConfigured(): Boolean {
        return appPreferences.isLoggedIn &&
            appPreferences.automaticBackupEnabled.get() &&
            appPreferences.automaticBackupTreeUri.get().isNotBlank()
    }

    private fun registerPreferenceListeners() {
        if (!listenersRegistered) {
            PreferenceManager.getDefaultSharedPreferences(context)
                .registerOnSharedPreferenceChangeListener(preferenceChangeListener)
            listenersRegistered = true
        }

        observedAccountPreferences
            ?.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        observedAccountPreferences = appPreferences.accountID.get()
            .takeIf(String::isNotBlank)
            ?.let { accountID ->
                context.getSharedPreferences("account_$accountID", Context.MODE_PRIVATE)
            }
            ?.also {
                it.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
            }
    }

    private fun unregisterPreferenceListeners() {
        if (listenersRegistered) {
            PreferenceManager.getDefaultSharedPreferences(context)
                .unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
            listenersRegistered = false
        }
        observedAccountPreferences
            ?.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        observedAccountPreferences = null
    }

    private fun cancelAll() {
        WorkManager.getInstance(context).run {
            cancelUniqueWork(PERIODIC_WORK_NAME)
            cancelUniqueWork(IMMEDIATE_WORK_NAME)
            cancelUniqueWork(CHANGE_WORK_NAME)
        }
    }

    companion object {
        internal const val PERIODIC_WORK_NAME = "automatic-backup-periodic"
        internal const val IMMEDIATE_WORK_NAME = "automatic-backup-now"
        internal const val CHANGE_WORK_NAME = "automatic-backup-change"
        private const val CHANGE_DEBOUNCE_MINUTES = 10L
        private val IGNORED_PREFERENCE_KEYS = setOf(
            "automatic_backup_enabled",
            "automatic_backup_tree_uri",
            "automatic_backup_retention",
            "automatic_backup_last_at",
            "automatic_backup_last_error",
        )
    }
}

internal fun automaticBackupConstraints(): Constraints {
    return Constraints.Builder()
        .setRequiresStorageNotLow(true)
        .build()
}
