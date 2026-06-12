package com.jocmp.capy

import com.jocmp.capy.accounts.AutoDelete
import com.jocmp.capy.accounts.Source
import com.jocmp.capy.common.TimeHelpers
import com.jocmp.capy.preferences.Preference
import com.jocmp.capy.preferences.PreferenceStore
import com.jocmp.capy.preferences.getEnum
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AccountPreferences(
    private val store: PreferenceStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val source: Preference<Source>
        get() = store.getEnum("source", Source.LOCAL)

    val username: Preference<String>
        get() = store.getString("username", "")

    val url: Preference<String>
        get() = store.getString("api_url", "")

    val clientCertAlias: Preference<String>
        get() = store.getString("client_cert_alias", "")

    val password: Preference<String>
        get() = store.getString("password", "")

    val autoDelete: Preference<AutoDelete>
        get() = store.getEnum("auto_delete_articles", AutoDelete.default)

    val filterKeywords: Preference<Set<String>>
        get() = store.getStringSet("keyword_blocklist")

    val automationRules: Preference<List<ArticleAutomationRule>>
        get() = store.getObject(
            key = "article_automation_rules",
            defaultValue = emptyList(),
            serializer = { json.encodeToString(it) },
            deserializer = { json.decodeFromString(it) },
        )

    val canSaveArticleExternally: Preference<Boolean>
        get() = store.getBoolean("can_save_article_externally", false)

    val lastRefreshedAt: Preference<Long>
        get() = store.getLong("last_refreshed_at", 0L)

    suspend fun touchLastRefreshedAt() {
        lastRefreshedAt.set(TimeHelpers.nowUTC().toEpochSecond())
    }
}
