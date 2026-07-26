package com.capyreader.app.preferences

import com.jocmp.capy.preferences.Preference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

internal class SecretPreference(
    private val key: String,
    private val secretStore: SecretStore,
    private val legacyPreference: Preference<String>,
) : Preference<String> {
    private val updates = MutableSharedFlow<String>(extraBufferCapacity = 1)

    override fun key(): String {
        return key
    }

    override fun get(): String {
        secretStore.get(key)?.let { return it }

        if (!legacyPreference.isSet()) {
            return defaultValue()
        }

        val legacyValue = legacyPreference.get()
        if (legacyValue.isBlank()) {
            legacyPreference.delete()
            return defaultValue()
        }

        if (secretStore.set(key, legacyValue)) {
            legacyPreference.delete()
        }

        return legacyValue
    }

    override fun set(value: String) {
        if (value.isBlank()) {
            delete()
            return
        }

        if (secretStore.set(key, value)) {
            legacyPreference.delete()
        }
        updates.tryEmit(get())
    }

    override fun isSet(): Boolean {
        return get().isNotBlank()
    }

    override fun delete() {
        secretStore.delete(key)
        legacyPreference.delete()
        updates.tryEmit(defaultValue())
    }

    override fun defaultValue(): String {
        return ""
    }

    override fun changes(): Flow<String> {
        return updates
            .onStart { emit(get()) }
            .conflate()
    }

    override fun stateIn(scope: CoroutineScope): StateFlow<String> {
        return changes().stateIn(scope, SharingStarted.Eagerly, get())
    }
}
