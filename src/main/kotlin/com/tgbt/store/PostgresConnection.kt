package com.tgbt.store

import com.vladsch.kotlin.jdbc.HikariCP
import com.vladsch.kotlin.jdbc.Session
import com.vladsch.kotlin.jdbc.SessionImpl
import com.vladsch.kotlin.jdbc.usingDefault
import java.net.URI

object PostgresConnection {
    init {
        val dbUri = URI(System.getenv("DATABASE_URL"))
        val (username: String, password: String) = dbUri.userInfo.split(":")
        val jdbcUrl = "jdbc:postgresql://${dbUri.host}:${dbUri.port}${dbUri.path}?currentSchema=public"
        HikariCP.default(jdbcUrl, username, password)
        SessionImpl.defaultDataSource = { HikariCP.dataSource() }
    }

    fun <R> inSession(block: Session.() -> R): R {
        return usingDefault { s -> s.block() }
    }

}