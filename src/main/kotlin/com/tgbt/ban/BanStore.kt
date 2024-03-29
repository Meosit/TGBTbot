package com.tgbt.ban

import com.tgbt.store.PostgresConnection
import com.vladsch.kotlin.jdbc.Row
import com.vladsch.kotlin.jdbc.sqlQuery
import java.sql.Timestamp

object BanStore {

    init {
        PostgresConnection.inSession {
            execute(sqlQuery(CREATE_TABLE_SQL))
        }
    }

    fun insert(ban: UserBan): Boolean = PostgresConnection.inSession {
        1 == update(
            sqlQuery(
                INSERT_SQL,
                ban.authorChatId,
                ban.authorName,
                ban.reason,
                ban.postTeaser,
                ban.bannedBy,
                ban.insertedTime,
                ban.lastUnbanRequestTime,
            )
        )
    }

    fun remove(chatId: Long): Boolean = PostgresConnection.inSession {
        1 == update(sqlQuery(DELETE_BY_CHAT_ID, chatId))
    }

    fun updateLastUnbanRequest(chatId: Long, lastUnbanRequest: Timestamp): Boolean = PostgresConnection.inSession {
        1 == update(sqlQuery(UPDATE_UNBAN_REQUEST_BY_CHAT_ID, lastUnbanRequest, chatId))
    }

    fun findByChatId(chatId: Long): UserBan? = PostgresConnection.inSession {
        first(sqlQuery(SELECT_BY_CHAT_ID, chatId)) { row -> row.toUserBan() }
    }

    fun findByChatIdOrName(nameOrChatId: String): UserBan? = PostgresConnection.inSession {
        first(sqlQuery(SELECT_BY_CHAT_ID_OR_NAME, nameOrChatId.toLongOrNull() ?: 0L, nameOrChatId.lowercase())) { row -> row.toUserBan() }
    }

    private fun Row.toUserBan() = UserBan(
        authorChatId = long("author_chat_id"),
        authorName = string("author_name"),
        reason = string("reason"),
        postTeaser = string("post_teaser"),
        bannedBy = string("banned_by"),
        insertedTime = sqlTimestamp("inserted_time"),
        lastUnbanRequestTime = sqlTimestamp("last_unban_request_time"),
    )

    private const val CREATE_TABLE_SQL = """
    CREATE TABLE IF NOT EXISTS ban_list (
      author_chat_id BIGINT NOT NULL,
      author_name TEXT NOT NULL,
      reason TEXT NOT NULL,
      post_teaser TEXT NOT NULL,
      banned_by TEXT NOT NULL,
      inserted_time TIMESTAMP NOT NULL,
      last_unban_request_time TIMESTAMP NOT NULL,
      PRIMARY KEY (author_chat_id)
    )"""

    private const val INSERT_SQL = """
    INSERT INTO ban_list (
        author_chat_id, 
        author_name, 
        reason, 
        post_teaser, 
        banned_by, 
        inserted_time,
        last_unban_request_time
    ) VALUES (?,?,?,?,?,?,?)"""

    private const val SELECT_BY_CHAT_ID =
        """SELECT * FROM ban_list WHERE author_chat_id = ?"""

    private const val SELECT_BY_CHAT_ID_OR_NAME =
        """SELECT * FROM ban_list WHERE author_chat_id = ? OR lower(author_name) = ?"""

    private const val DELETE_BY_CHAT_ID =
        """DELETE FROM ban_list WHERE author_chat_id = ?"""

    private const val UPDATE_UNBAN_REQUEST_BY_CHAT_ID =
        """UPDATE ban_list SET last_unban_request_time = ? WHERE author_chat_id = ?"""

}