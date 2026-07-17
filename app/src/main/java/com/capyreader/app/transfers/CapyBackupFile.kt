package com.capyreader.app.transfers

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import androidx.preference.PreferenceManager
import com.capyreader.app.R
import com.capyreader.app.common.toast
import com.jocmp.capy.Account
import com.jocmp.capy.ArticleBackupReference
import com.jocmp.capy.SavedSearchBackupEntry
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
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class CapyBackupFile(
    private val context: Context,
) {
    suspend fun export(account: Account, target: Uri?) {
        target ?: return

        val result = runCatching {
            withContext(Dispatchers.IO) {
                writeBackup(account, target)
            }
        }

        context.toast(
            result.fold(
                onSuccess = { R.string.backup_exporter_success },
                onFailure = { R.string.backup_exporter_failure },
            )
        )
    }

    suspend fun exportAutomatic(
        account: Account,
        treeUri: Uri,
        retention: Int,
        exportedAt: Instant = Instant.now(),
    ): Uri = withContext(Dispatchers.IO) {
        val rootDocumentUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        val timestamp = AUTOMATIC_BACKUP_FILE_FORMAT.format(
            exportedAt.atZone(ZoneOffset.UTC)
        )
        val target = DocumentsContract.createDocument(
            context.contentResolver,
            rootDocumentUri,
            BACKUP_MIME_TYPE,
            "$AUTOMATIC_BACKUP_FILE_PREFIX$timestamp.json",
        ) ?: throw IOException("Could not create automatic backup")

        try {
            writeBackup(account, target, exportedAt)
        } catch (error: Throwable) {
            runCatching {
                DocumentsContract.deleteDocument(context.contentResolver, target)
            }
            throw error
        }
        pruneAutomaticBackups(treeUri, retention.coerceIn(MIN_RETENTION, MAX_RETENTION))
        target
    }

    suspend fun restorePreview(account: Account, source: Uri?): BackupRestorePreview? {
        source ?: return null

        val result = runCatching {
            withContext(Dispatchers.IO) {
                val backup = readBackup(source)

                validateBackup(account, backup)

                BackupRestorePreview(
                    version = backup.version,
                    source = backup.account.source,
                    hasSubscriptions = backup.account.subscriptionsOpml.ifBlank {
                        backup.subscriptionsOpml
                    }.isNotBlank(),
                    savedSearchCount = backup.savedSearches.size,
                    readLaterCount = backup.readLater.size,
                    starredCount = backup.starred.size,
                    hasRules = backup.rules != null,
                    hasAiSettings = backup.ai.settings.isNotEmpty(),
                )
            }
        }

        result.exceptionOrNull()?.let { context.toast(showRestoreFailure(it)) }

        return result.getOrNull()
    }

    suspend fun restore(
        account: Account,
        source: Uri?,
        mode: BackupRestoreMode = BackupRestoreMode.REPLACE,
    ) {
        source ?: return

        val result = runCatching {
            withContext(Dispatchers.IO) {
                val backup = readBackup(source)

                validateBackup(account, backup)

                val appPreferenceValues = backup.app?.preferences ?: backup.appPreferences
                val accountPreferenceValues = backup.account.preferences
                val subscriptionsOpml = backup.account.subscriptionsOpml.ifBlank {
                    backup.subscriptionsOpml
                }

                restoreValues(
                    preferences = appPreferences(),
                    values = appPreferenceValues,
                    keysToKeep = EXCLUDED_APP_KEYS + ACCOUNT_ID_KEY,
                    mode = mode,
                )
                restoreValues(
                    preferences = accountPreferences(account),
                    values = accountPreferenceValues,
                    keysToKeep = SENSITIVE_ACCOUNT_KEYS,
                    mode = mode,
                )

                if (subscriptionsOpml.isNotBlank()) {
                    account.import(subscriptionsOpml.byteInputStream()) {}
                }

                account.restoreSavedSearchBackupEntries(
                    backup.savedSearches.map { it.toSavedSearchBackupEntry() },
                )
                account.restoreStarredArticleBackupIDs(backup.starred)
                account.restoreReadLaterArticleBackupReferences(
                    backup.readLater.map { it.toArticleBackupReference() },
                )

                account.refresh()
            }
        }

        context.toast(
            result.fold(
                onSuccess = { R.string.backup_importer_success },
                onFailure = ::showRestoreFailure,
            )
        )
    }

    private suspend fun writeBackup(
        account: Account,
        target: Uri,
        exportedAt: Instant = Instant.now(),
    ) {
        val source = json.encodeToString(createBackup(account, exportedAt)).toByteArray()
        val output = context.contentResolver.openOutputStream(target, "w")
            ?: throw IOException("Could not open backup file")

        output.use { it.write(source) }
    }

    private suspend fun createBackup(
        account: Account,
        exportedAt: Instant,
    ): BackupDocument {
        return BackupDocument(
            version = BACKUP_VERSION,
            exportedAt = exportedAt.toString(),
            app = BackupApp(
                preferences = appPreferences().backupValues(excludedKeys = EXCLUDED_APP_KEYS),
            ),
            account = BackupAccount(
                source = account.source,
                preferences = accountPreferences(account).backupValues(excludedKeys = SENSITIVE_ACCOUNT_KEYS),
                subscriptionsOpml = account.opmlDocument(),
            ),
            rules = backupSectionValue(accountPreferences(account), "article_automation_rules"),
            savedSearches = account.savedSearchBackupEntries().map {
                BackupSavedSearch.from(it)
            },
            readLater = account.readLaterArticleBackupReferences().map {
                BackupArticleReference.from(it)
            },
            starred = account.starredArticleBackupIDs(),
            ai = BackupAi(
                settings = appPreferences().backupValues(
                    includedKeys = { it.startsWith("ai_") },
                    excludedKeys = SENSITIVE_APP_KEYS,
                ),
                includeApiKey = false,
            ),
        )
    }

    private fun pruneAutomaticBackups(treeUri: Uri, retention: Int) {
        automaticBackupsToDelete(
            documents = automaticBackupDocuments(treeUri),
            retention = retention,
        )
            .forEach { document ->
                val uri = DocumentsContract.buildDocumentUriUsingTree(
                    treeUri,
                    document.documentID,
                )
                if (!DocumentsContract.deleteDocument(context.contentResolver, uri)) {
                    throw IOException("Could not delete old automatic backup")
                }
            }
    }

    private fun automaticBackupDocuments(treeUri: Uri): List<AutomaticBackupDocument> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )

        val cursor = context.contentResolver.query(
            childrenUri,
            projection,
            null,
            null,
            null,
        ) ?: throw IOException("Could not list automatic backups")

        return cursor.use {
            val idIndex = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID
            )
            val nameIndex = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            )
            val modifiedIndex = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )

            buildList {
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex) ?: continue
                    if (!name.startsWith(AUTOMATIC_BACKUP_FILE_PREFIX) ||
                        !name.endsWith(".json")
                    ) {
                        continue
                    }

                    add(
                        AutomaticBackupDocument(
                            documentID = cursor.getString(idIndex),
                            name = name,
                            lastModified = cursor.getLong(modifiedIndex),
                        )
                    )
                }
            }
        }
    }

    private fun readBackup(source: Uri): BackupDocument {
        return context.contentResolver.openInputStream(source)?.use {
            json.decodeFromString<BackupDocument>(it.reader().readText())
        } ?: error("Could not open backup file")
    }

    private fun validateBackup(account: Account, backup: BackupDocument) {
        if (backup.version !in SUPPORTED_BACKUP_VERSIONS) {
            throw UnsupportedVersionError(backup.version)
        }

        if (backup.account.source != account.source) {
            throw SourceMismatchError(backup.account.source, account.source)
        }
    }

    private fun showRestoreFailure(error: Throwable): Int {
        CapyLog.error("backup_importer", error)

        return if (error is SourceMismatchError) {
            R.string.backup_importer_source_mismatch
        } else {
            R.string.backup_importer_failure
        }
    }

    private fun appPreferences(): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    private fun accountPreferences(account: Account): SharedPreferences {
        return context.getSharedPreferences("account_${account.id}", Context.MODE_PRIVATE)
    }

    private fun SharedPreferences.backupValues(
        includedKeys: (String) -> Boolean = { true },
        excludedKeys: Set<String> = emptySet(),
    ): Map<String, BackupPreferenceValue> {
        return all.mapNotNull { (key, value) ->
            if (!includedKeys(key) || key in excludedKeys) {
                return@mapNotNull null
            }

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
        mode: BackupRestoreMode,
    ) {
        restorePreferenceValues(
            preferences = preferences,
            values = values,
            keysToKeep = keysToKeep,
            mode = mode,
        )
    }

    private fun backupSectionValue(preferences: SharedPreferences, key: String): JsonElement? {
        val value = preferences.all[key] as? String ?: return null

        return runCatching { json.parseToJsonElement(value) }.getOrElse {
            JsonPrimitive(value)
        }
    }

    private class SourceMismatchError(
        val backupSource: Source,
        val currentSource: Source,
    ) : Throwable("Backup source $backupSource does not match current source $currentSource")

    private class UnsupportedVersionError(
        val version: Int,
    ) : Throwable("Backup version $version is not supported")

    companion object {
        const val DEFAULT_FILE_NAME = "capy-backup.json"

        private const val BACKUP_VERSION = 2
        private const val ACCOUNT_ID_KEY = "account_id"
        private const val BACKUP_MIME_TYPE = "application/json"
        private const val AUTOMATIC_BACKUP_FILE_PREFIX = "meerkat-backup-"
        private const val MIN_RETENTION = 1
        private const val MAX_RETENTION = 30
        private val SUPPORTED_BACKUP_VERSIONS = setOf(1, BACKUP_VERSION)
        private val SENSITIVE_APP_KEYS = setOf("ai_api_key")
        private val SENSITIVE_ACCOUNT_KEYS = setOf("password")
        private val DEVICE_LOCAL_APP_KEYS = setOf(
            "automatic_backup_enabled",
            "automatic_backup_tree_uri",
            "automatic_backup_last_at",
            "automatic_backup_last_error",
        )
        private val EXCLUDED_APP_KEYS = SENSITIVE_APP_KEYS + DEVICE_LOCAL_APP_KEYS
        private val AUTOMATIC_BACKUP_FILE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

        private val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }
    }
}

internal data class AutomaticBackupDocument(
    val documentID: String,
    val name: String,
    val lastModified: Long,
)

internal val AUTOMATIC_BACKUP_ORDER =
    compareByDescending<AutomaticBackupDocument> { it.lastModified }
        .thenByDescending { it.name }

internal fun automaticBackupsToDelete(
    documents: List<AutomaticBackupDocument>,
    retention: Int,
): List<AutomaticBackupDocument> {
    return documents
        .sortedWith(AUTOMATIC_BACKUP_ORDER)
        .drop(retention.coerceIn(1, 30))
}

enum class BackupRestoreMode {
    REPLACE,
    MERGE,
}

internal fun restorePreferenceValues(
    preferences: SharedPreferences,
    values: Map<String, BackupPreferenceValue>,
    keysToKeep: Set<String> = emptySet(),
    mode: BackupRestoreMode,
) {
    val keptValues = keysToKeep.mapNotNull { key ->
        preferences.all[key]?.let { key to it }
    }

    preferences.edit().apply {
        if (mode == BackupRestoreMode.REPLACE) {
            clear()
        }

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

data class BackupRestorePreview(
    val version: Int,
    val source: Source,
    val hasSubscriptions: Boolean,
    val savedSearchCount: Int,
    val readLaterCount: Int,
    val starredCount: Int,
    val hasRules: Boolean,
    val hasAiSettings: Boolean,
)

@Serializable
private data class BackupDocument(
    val version: Int = 1,
    val exportedAt: String,
    val account: BackupAccount,
    val app: BackupApp? = null,
    val rules: JsonElement? = null,
    val savedSearches: List<BackupSavedSearch> = emptyList(),
    val readLater: List<BackupArticleReference> = emptyList(),
    val starred: List<String> = emptyList(),
    val ai: BackupAi = BackupAi(),
    val appPreferences: Map<String, BackupPreferenceValue> = emptyMap(),
    val subscriptionsOpml: String = "",
)

@Serializable
private data class BackupApp(
    val preferences: Map<String, BackupPreferenceValue> = emptyMap(),
)

@Serializable
private data class BackupAccount(
    val source: Source,
    val preferences: Map<String, BackupPreferenceValue> = emptyMap(),
    val subscriptionsOpml: String = "",
)

@Serializable
private data class BackupAi(
    val settings: Map<String, BackupPreferenceValue> = emptyMap(),
    val includeApiKey: Boolean = false,
)

@Serializable
private data class BackupSavedSearch(
    val id: String,
    val name: String,
    val query: String? = null,
    val showUnreadBadge: Boolean = true,
    val articleIDs: List<String> = emptyList(),
) {
    fun toSavedSearchBackupEntry() =
        SavedSearchBackupEntry(
            id = id,
            name = name,
            query = query,
            showUnreadBadge = showUnreadBadge,
            articleIDs = articleIDs,
        )

    companion object {
        fun from(entry: SavedSearchBackupEntry) =
            BackupSavedSearch(
                id = entry.id,
                name = entry.name,
                query = entry.query,
                showUnreadBadge = entry.showUnreadBadge,
                articleIDs = entry.articleIDs,
            )
    }
}

@Serializable
private data class BackupArticleReference(
    val id: String,
    val url: String? = null,
) {
    fun toArticleBackupReference() =
        ArticleBackupReference(
            id = id,
            url = url,
        )

    companion object {
        fun from(entry: ArticleBackupReference) =
            BackupArticleReference(
                id = entry.id,
                url = entry.url,
            )
    }
}

@Serializable
internal data class BackupPreferenceValue(
    val type: String,
    val value: JsonElement,
)

internal const val TYPE_STRING = "string"
internal const val TYPE_LONG = "long"
internal const val TYPE_INT = "int"
internal const val TYPE_FLOAT = "float"
internal const val TYPE_BOOLEAN = "boolean"
internal const val TYPE_STRING_SET = "string_set"
