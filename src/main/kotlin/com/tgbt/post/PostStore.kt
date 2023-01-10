package com.tgbt.post

import com.tgbt.store.PostgresConnection
import com.vladsch.kotlin.jdbc.sqlQuery
import java.time.Instant

object PostStore {

    init {
        PostgresConnection.inSession {
            execute(sqlQuery(CREATE_TABLE_SQL))
        }
    }

    fun insert(post: Post): Boolean =
        1 == PostgresConnection.inSession { update(sqlQuery(INSERT_SQL, post.id, post.unixTime)) }

    fun cleanupOldPosts(retentionDays: Int): Int = PostgresConnection.inSession {
        val leastPreservedTime = Instant.now().epochSecond - (retentionDays * 86400)
        update(sqlQuery(DELETE_SQL, leastPreservedTime))
    }

    fun isPostedToTG(post: Post) = PostgresConnection.inSession {
        first(sqlQuery(IS_ALREADY_POSTED_SQL, post.id)) { it.boolean("exst") } ?: false
    }


    private const val CREATE_TABLE_SQL = """
    CREATE TABLE IF NOT EXISTS tg_posts (
      id BIGINT NOT NULL,
      unix_time BIGINT NOT NULL,
      PRIMARY KEY (id)
    )"""

    private const val IS_ALREADY_POSTED_SQL = """SELECT EXISTS(SELECT 1 FROM tg_posts WHERE id = ?) AS exst"""

    private const val INSERT_SQL = """INSERT INTO tg_posts (id, unix_time) VALUES (?, ?)"""

    private const val DELETE_SQL = """DELETE FROM tg_posts WHERE unix_time <= ?"""

}