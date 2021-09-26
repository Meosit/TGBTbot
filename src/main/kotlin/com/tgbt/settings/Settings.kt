package com.tgbt.settings

import java.util.concurrent.atomic.AtomicReference

class Settings(private val store: SettingStore) {

    private val settings by lazy {
        AtomicReference(store.selectAll())
    }

    operator fun set(key: Setting, value: String) {
        if (store.insertOrUpdate(key.name, value)) {
            settings.updateAndGet { it + (key.name to value) }
        }
    }

    operator fun get(key: Setting): String = settings.get().getValue(key.name)

    fun putIfAbsent(key: Setting, value: String) {
        synchronized(this) {
            if (!settings.get().containsKey(key.name)) {
                this[key] = value
            }
        }
    }

}