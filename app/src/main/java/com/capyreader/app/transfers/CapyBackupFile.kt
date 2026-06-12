package com.capyreader.app.transfers

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.preference.PreferenceManager
import com.capyreader.app.R
import com.capyreader.app.common.toast
import com.jocmp.capy.Account
import com.jocmp.capy.accounts.Source
import com.jocmp.capy.logging.CapyLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.FileOutputStream
import java.time.Instant

class CapyBackupFile(
    private val context: Context,
) {
    suspend fun export(account: Account, target: Uri?) {
        target ?: return

        val result = runCatching {
            withContext(Dispatchers.IO) {
                val backup = BackupDocument(
                    exportedAt = Instant.now().toString(),
                    account = BackupAccount(
                        source = account.source,
                        preferences = accountPreferences(account).backupValues(),
                    ),
                    appPreferences = appPreferences().backupValues(),
                    subscriptionsOpml = account.opmlDocument(),
                )
                val source = json.encodeToString(backup).toByteArray()

                context.contentResolver.openFileDescriptor(target, "w")?.use { descriptor ->
                    FileOutputStream(descriptor.fileDescriptor).use {
                        it.write(source)
                    }
                }
            }
        }

        context.toast(
            result.fold(
                onSuccess = { R.string.backup_exporter_success },
                onFailure = { R.string.backup_exporter_failure },
            )
        )
    }

    suspend fun restore(account: Account, source: Uri?) {
        source ?: return

        val result = runCatching {
            withContext(Dispatchers.IO) {
                val backup = context.contentResolver.openInputStream(source)?.use {
                    json.decodeFromString<BackupDocument>(it.reader().readText())
                } ?: error("Could not open backup file")

                if (backup.account.source != account.source) {
                    throw SourceMismatchError(backup.account.source, account.source)
                }

                restoreValues(
                    preferences = appPreferences(),
                    values = backup.appPreferences,
                    keysToKeep = setOf(ACCOUNT_ID_KEY),
                )
                restoreValues(
                    preferences = accountPreferences(account),
                    values = backup.account.preferences,
                )

                if (backup.subscriptionsOpml.isNotBlank()) {
                    account.import(backup.subscriptionsOpml.byteInputStream()) {}
                }
                account.refresh()
            }
        }

        context.toast(
            result.fold(
                onSuccess = { R.string.backup_importer_success },
                onFailure = { error ->
                    CapyLog.error("backup_importer", error)

                    if (error is SourceMismatchError) {
                        R.string.backup_importer_source_mismatch
                    } else {
                        R.string.backup_importer_failure
                    }
                },
            )
        )
    }

    private fun appPreferences(): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    private fun accountPreferences(account: Account): SharedPreferences {
        return context.getSharedPreferences("account_${account.id}", Context.MODE_PRIVATE)
    }

    private fun SharedPreferences.backupValues(): Map<String, BackupPreferenceValue> {
        return all.mapNotNull { (key, value) ->
            val backupValue = when (value) {
                is String -> BackupPreferenceValue(TYPE_STRING, JsonPrimitive(value))
                is Long -> BackupPreferenceValue(TYPE_LONG, JsonPrimitive(value))
                is Int -> BackupPreferenceValue(TYPE_INT, JsonPrimitive(value))
                is Float -> BackupPreferenceValue(TYPE_FLOAT, JsonPrimitive(value))
                is Boolean -> BackupPreferenceValue(TYPE_BOOLEAN, JsonPrimitive(value))
                is Set<*> -> BackupPreferenceValue(
                    TYPE_STRING_SET,
                    JsonArray(value.filterIsInstance<String>().map(::JsonPrimitive)),
                )
                else -> null
            }

            backupValue?.let { key to it }
        }.toMap()
    }

    private fun restoreValues(
        preferences: SharedPreferences,
        values: Map<String, BackupPreferenceValue>,
        keysToKeep: Set<String> = emptySet(),
    ) {
        val keptValues = keysToKeep.mapNotNull { key ->
            preferences.all[key]?.let { key to it }
        }

        preferences.edit().clear().apply()

        preferences.edit().apply {
            keptValues.forEach { (key, value) ->
                putPreferenceValue(key, value)
            }

            values.forEach { (key, value) ->
                if (key !in keysToKeep) {
                    putBackupValue(key, value)
                }
            }
        }.apply()
    }

    private fun SharedPreferences.Editor.putPreferenceValue(key: String, value: Any) {
        when (value) {
            is String -> putString(key, value)
            is Long -> putLong(key, value)
            is Int -> putInt(key, value)
            is Float -> putFloat(key, value)
            is Boolean -> putBoolean(key, value)
            is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
        }
    }

    private fun SharedPreferences.Editor.putBackupValue(key: String, value: BackupPreferenceValue) {
        val jsonValue = value.value

        when (value.type) {
            TYPE_STRING -> putString(key, jsonValue.stringValue())
            TYPE_LONG -> putLong(key, jsonValue.stringValue().toLong())
            TYPE_INT -> putInt(key, jsonValue.stringValue().toInt())
            TYPE_FLOAT -> putFloat(key, jsonValue.stringValue().toFloat())
            TYPE_BOOLEAN -> putBoolean(key, jsonValue.stringValue().toBoolean())
            TYPE_STRING_SET -> putStringSet(
                key,
                jsonValue.jsonArray.mapNotNull { it.stringOrNull() }.toSet(),
            )
        }
    }

    private fun JsonElement.stringValue(): String {
        return jsonPrimitive.content
    }

    private fun JsonElement.stringOrNull(): String? {
        return runCatching { jsonPrimitive.content }.getOrNull()
    }

    private class SourceMismatchError(
        val backupSource: Source,
        val currentSource: Source,
    ) : Throwable("Backup source $backupSource does not match current source $currentSource")

    companion object {
        const val DEFAULT_FILE_NAME = "capy-backup.json"

        private const val ACCOUNT_ID_KEY = "account_id"
        private const val TYPE_STRING = "string"
        private const val TYPE_LONG = "long"
        private const val TYPE_INT = "int"
        private const val TYPE_FLOAT = "float"
        private const val TYPE_BOOLEAN = "boolean"
        private const val TYPE_STRING_SET = "string_set"

        private val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }
    }
}

@Serializable
private data class BackupDocument(
    val version: Int = 1,
    val exportedAt: String,
    val account: BackupAccount,
    val appPreferences: Map<String, BackupPreferenceValue> = emptyMap(),
    val subscriptionsOpml: String = "",
)

@Serializable
private data class BackupAccount(
    val source: Source,
    val preferences: Map<String, BackupPreferenceValue> = emptyMap(),
)

@Serializable
private data class BackupPreferenceValue(
    val type: String,
    val value: JsonElement,
)
