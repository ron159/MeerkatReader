package com.capyreader.app.transfers

import android.content.Context
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class BackupPreferenceRestoreTest {
    @Test
    fun `merge overwrites provided values and preserves other settings`() {
        val preferences = preferences("merge").apply {
            edit()
                .putString("existing_only", "keep")
                .putString("shared", "old")
                .putString("password", "secret")
                .commit()
        }

        restorePreferenceValues(
            preferences = preferences,
            values = mapOf(
                "shared" to BackupPreferenceValue(TYPE_STRING, JsonPrimitive("new")),
                "backup_only" to BackupPreferenceValue(TYPE_BOOLEAN, JsonPrimitive(true)),
                "password" to BackupPreferenceValue(TYPE_STRING, JsonPrimitive("ignored")),
            ),
            keysToKeep = setOf("password"),
            mode = BackupRestoreMode.MERGE,
        )

        assertEquals("keep", preferences.getString("existing_only", null))
        assertEquals("new", preferences.getString("shared", null))
        assertEquals(true, preferences.getBoolean("backup_only", false))
        assertEquals("secret", preferences.getString("password", null))
    }

    @Test
    fun `replace removes other settings but preserves protected values`() {
        val preferences = preferences("replace").apply {
            edit()
                .putString("existing_only", "remove")
                .putString("account_id", "current-account")
                .putString("ai_api_key", "secret")
                .commit()
        }

        restorePreferenceValues(
            preferences = preferences,
            values = mapOf(
                "restored" to BackupPreferenceValue(TYPE_INT, JsonPrimitive(42)),
                "account_id" to BackupPreferenceValue(TYPE_STRING, JsonPrimitive("backup-account")),
            ),
            keysToKeep = setOf("account_id", "ai_api_key"),
            mode = BackupRestoreMode.REPLACE,
        )

        assertFalse(preferences.contains("existing_only"))
        assertEquals(42, preferences.getInt("restored", 0))
        assertEquals("current-account", preferences.getString("account_id", null))
        assertEquals("secret", preferences.getString("ai_api_key", null))
    }

    private fun preferences(name: String) =
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("backup-restore-$name", Context.MODE_PRIVATE)
}
