package com.tgbt.ban

import com.vladsch.kotlin.jdbc.Row
import com.vladsch.kotlin.jdbc.sqlQuery
import com.vladsch.kotlin.jdbc.usingDefault

class BanStore {

    init {
        usingDefault { session ->
            session.execute(sqlQuery(CREATE_TABLE_SQL))
        }
    }

    fun insert(suggestion: UserBan): Boolean = usingDefault { session ->
        1 == session.update(
            sqlQuery(
                INSERT_SQL,
                suggestion.authorChatId,
                suggestion.authorName,
                suggestion.reason,
                suggestion.postTeaser,
                suggestion.bannedBy,
                suggestion.insertedTime
            )
        )
    }

    fun remove(chatId: Long): Boolean = usingDefault { session ->
        1 == session.update(sqlQuery(DELETE_BY_CHAT_ID, chatId))
    }

    fun findByChatId(chatId: Long): UserBan? = usingDefault { session ->
        session.first(sqlQuery(SELECT_BY_CHAT_ID, chatId)) { row -> row.toUserBan() }
    }

    fun findByChatIdOrName(nameOrChatId: String): UserBan? = usingDefault { session ->
        session.first(sqlQuery(SELECT_BY_CHAT_ID_OR_NAME, nameOrChatId.toLongOrNull() ?: 0L, nameOrChatId)) { row -> row.toUserBan() }
    }

    private fun Row.toUserBan() = UserBan(
        authorChatId = long("author_chat_id"),
        authorName = string("author_name"),
        reason = string("reason"),
        postTeaser = string("post_teaser"),
        bannedBy = string("banned_by"),
        insertedTime = sqlTimestamp("inserted_time"),
    )

    companion object {
        private const val CREATE_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS ban_list (
          author_chat_id BIGINT NOT NULL,
          author_name TEXT NOT NULL,
          reason TEXT NOT NULL,
          post_teaser TEXT NOT NULL,
          banned_by TEXT NOT NULL,
          inserted_time TIMESTAMP NOT NULL,
          PRIMARY KEY (author_chat_id)
        )"""

        private const val INSERT_SQL = """
        INSERT INTO ban_list (
            author_chat_id, 
            author_name, 
            reason, 
            post_teaser, 
            banned_by, 
            inserted_time
        ) VALUES (?,?,?,?,?,?)"""

        private const val SELECT_BY_CHAT_ID =
            """SELECT * FROM ban_list WHERE author_chat_id = ?"""

        private const val SELECT_BY_CHAT_ID_OR_NAME =
            """SELECT * FROM ban_list WHERE author_chat_id = ? OR author_name = ?"""

        private const val DELETE_BY_CHAT_ID =
            """DELETE FROM ban_list WHERE author_chat_id = ?"""
    }

}