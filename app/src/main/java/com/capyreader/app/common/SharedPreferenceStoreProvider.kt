package com.capyreader.app.common

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.core.content.edit
import com.jocmp.capy.AccountPreferences
import com.jocmp.capy.PreferenceStoreProvider
import com.capyreader.app.preferences.ACCOUNT_SECRET_KEY_ALIAS
import com.capyreader.app.preferences.ACCOUNT_SECRET_PREFERENCES_NAME
import com.capyreader.app.preferences.AndroidKeystoreSecretStore
import com.capyreader.app.preferences.AndroidPreferenceStore
import com.capyreader.app.preferences.SecretPreference
import com.capyreader.app.preferences.SecretStore

class SharedPreferenceStoreProvider internal constructor(
    private val context: Context,
    private val secretStore: SecretStore,
) : PreferenceStoreProvider {
    constructor(context: Context) : this(
        context = context,
        secretStore = AndroidKeystoreSecretStore(
            context = context,
            preferencesName = ACCOUNT_SECRET_PREFERENCES_NAME,
            keyAlias = ACCOUNT_SECRET_KEY_ALIAS,
        ),
    )

    override fun build(accountID: String): AccountPreferences {
        val preferenceStore = AndroidPreferenceStore(
            buildPreferences(context, accountID)
        )

        return AccountPreferences(
            store = preferenceStore,
            passwordPreference = SecretPreference(
                key = accountPasswordSecretKey(accountID),
                secretStore = secretStore,
                legacyPreference = preferenceStore.getString(PASSWORD_KEY),
            ),
        )
    }

    override fun delete(accountID: String) {
        secretStore.delete(accountPasswordSecretKey(accountID))
        val preferences = buildPreferences(context, accountID)

        preferences.edit(commit = true) {
            clear()
        }
    }
}

private fun buildPreferences(context: Context, accountID: String) =
    context.getSharedPreferences(accountPreferencesName(accountID), MODE_PRIVATE)

internal fun accountPreferencesName(accountID: String) = "account_$accountID"

internal fun accountPasswordSecretKey(accountID: String) = "account:$accountID:password"

private const val PASSWORD_KEY = "password"
