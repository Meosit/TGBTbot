package com.tgbt.settings

import com.vladsch.kotlin.jdbc.sqlQuery
import com.vladsch.kotlin.jdbc.usingDefault

class SettingStore {

    init {
        usingDefault { session ->
            session.execute(sqlQuery(CREATE_TABLE_SQL))
        }
    }

    fun selectAll(): Map<String, String> = usingDefault { session ->
        session
            .list(sqlQuery(SELECT_ALL_SQL)) { it.string("setting_key") to it.string("setting_value") }
            .toMap()
    }

    fun insertOrUpdate(key: String, value: String): Boolean = usingDefault { session ->
        1 == session.update(sqlQuery(UPSERT_SQL, key, value))
    }


    companion object {
        private const val CREATE_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS user_settings (
          setting_key TEXT NOT NULL,
          setting_value TEXT NOT NULL,
          PRIMARY KEY (setting_key)
        )"""

        private const val SELECT_ALL_SQL = """SELECT * FROM user_settings"""

        private const val UPSERT_SQL = """
        INSERT INTO user_settings (setting_key, setting_value) 
        VALUES (?, ?)
        ON CONFLICT (setting_key) DO UPDATE SET setting_value = EXCLUDED.setting_value
        """
    }

}