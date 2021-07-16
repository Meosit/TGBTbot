package com.tgbt.settings

import kotlinx.atomicfu.atomic

class Settings(private val store: SettingStore) {

    private val settings by lazy {
        atomic(store.selectAll())
    }

    operator fun set(key: Setting, value: String) {
        if (store.insertOrUpdate(key.name, value)) {
            settings.value = settings.value + (key.name to value)
        }
    }

    operator fun get(key: Setting): String = settings.value.getValue(key.name)

    fun putIfAbsent(key: Setting, value: String) {
        synchronized(this) {
            if (!settings.value.containsKey(key.name)) {
                this[key] = value
            }
        }
    }

}