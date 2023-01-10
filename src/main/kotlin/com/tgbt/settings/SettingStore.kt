package com.tgbt.settings

import com.tgbt.store.PostgresConnection
import com.vladsch.kotlin.jdbc.sqlQuery

object SettingStore {

    init {
        PostgresConnection.inSession {
            execute(sqlQuery(CREATE_TABLE_SQL))
        }
    }

    fun selectAll(): Map<String, String> = PostgresConnection.inSession {
        list(sqlQuery(SELECT_ALL_SQL)) { it.string("setting_key") to it.string("setting_value") }.toMap()
    }

    fun insertOrUpdate(key: String, value: String): Boolean = PostgresConnection.inSession {
        1 == update(sqlQuery(UPSERT_SQL, key, value))
    }


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