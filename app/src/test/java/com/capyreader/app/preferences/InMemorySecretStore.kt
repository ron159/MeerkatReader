package com.capyreader.app.preferences

internal class InMemorySecretStore(
    var failWrites: Boolean = false,
) : SecretStore {
    private val values = mutableMapOf<String, String>()

    override fun get(key: String): String? {
        return values[key]
    }

    override fun set(key: String, value: String): Boolean {
        if (failWrites) {
            return false
        }
        values[key] = value
        return true
    }

    override fun delete(key: String): Boolean {
        values.remove(key)
        return true
    }

    override fun clear(): Boolean {
        values.clear()
        return true
    }
}
