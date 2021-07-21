package com.tgbt.suggestion

import com.vladsch.kotlin.jdbc.Row
import com.vladsch.kotlin.jdbc.sqlQuery
import com.vladsch.kotlin.jdbc.usingDefault

class SuggestionStore {

    init {
        usingDefault { session ->
            session.execute(sqlQuery(CREATE_TABLE_SQL))
        }
    }

    fun insert(suggestion: UserSuggestion): Boolean = usingDefault { session ->
        1 == session.update(
            sqlQuery(
                INSERT_SQL,
                suggestion.authorMessageId,
                suggestion.authorChatId,
                suggestion.authorName,
                suggestion.editorMessageId,
                suggestion.editorChatId,
                suggestion.editorComment,
                suggestion.status.toString(),
                suggestion.postText,
                suggestion.imageId,
                suggestion.insertedTime
            )
        )
    }

    fun removeAllOlderThan(days: Int): Int = usingDefault { session ->
        session.update(sqlQuery(DELETE_ALL_OLDER_THAN.replace("<days>", days.toString())))
    }

    fun removeByChatAndMessageId(chatId: Long, messageId: Long, byAuthor: Boolean): Boolean = usingDefault { session ->
        val statement = DELETE_BY_CHAT_AND_MESSAGE_ID.replace("<user>", if (byAuthor) "author" else "editor")
        session.update(sqlQuery(statement, chatId, messageId)) == 1
    }

    fun findByChatAndMessageId(chatId: Long, messageId: Long, byAuthor: Boolean): UserSuggestion? =
        usingDefault { session ->
            val statement = SELECT_BY_CHAT_AND_MESSAGE_ID.replace("<user>", if (byAuthor) "author" else "editor")
            session.first(sqlQuery(statement, chatId, messageId)) { row -> row.toUserSuggestion() }
        }

    fun findLastByAuthorChatId(chatId: Long): UserSuggestion? = usingDefault { session ->
        session.first(sqlQuery(SELECT_LAST_BY_CHAT_ID, chatId)) { row -> row.toUserSuggestion() }
    }

    fun findByStatus(status: SuggestionStatus): List<UserSuggestion> = usingDefault { session ->
        session.list(sqlQuery(SELECT_BY_STATUS, status.toString())) { row -> row.toUserSuggestion() }
    }

    fun update(suggestion: UserSuggestion, byAuthor: Boolean) = usingDefault { session ->
        val statement = UPDATE_BY_CHAT_AND_MESSAGE_ID.replace("<user>", if (byAuthor) "author" else "editor")
        val query = sqlQuery(
            statement,
            suggestion.editorMessageId,
            suggestion.editorChatId,
            suggestion.editorComment,
            suggestion.status.toString(),
            suggestion.postText,
            suggestion.imageId,
            if (byAuthor) suggestion.authorChatId else suggestion.editorChatId,
            if (byAuthor) suggestion.authorMessageId else suggestion.editorMessageId
        )
        session.update(query)
    }

    private fun Row.toUserSuggestion() = UserSuggestion(
        authorMessageId = long("author_message_id"),
        authorChatId = long("author_chat_id"),
        authorName = string("author_name"),
        editorMessageId = longOrNull("editor_message_id"),
        editorChatId = longOrNull("editor_chat_id"),
        editorComment = stringOrNull("editor_comment") ?: "",
        status = SuggestionStatus.valueOf(string("status")),
        postText = string("post_text"),
        imageId = stringOrNull("image_id"),
        insertedTime = sqlTimestamp("inserted_time")
    )

    companion object {
        private const val CREATE_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS user_suggestions (
          author_message_id BIGINT NOT NULL,
          author_chat_id BIGINT NOT NULL,
          author_name TEXT NOT NULL,
          editor_message_id BIGINT NULL,
          editor_chat_id BIGINT NULL,
          editor_comment TEXT NULL,
          status TEXT NOT NULL,
          post_text TEXT NOT NULL,
          image_id TEXT NULL,
          inserted_time TIMESTAMP NOT NULL,
          PRIMARY KEY (author_message_id, author_chat_id)
        )"""

        private const val INSERT_SQL = """
        INSERT INTO user_suggestions (
            author_message_id, 
            author_chat_id, 
            author_name, 
            editor_message_id, 
            editor_chat_id, 
            editor_comment, 
            status, 
            post_text, 
            image_id, 
            inserted_time
        ) VALUES (?,?,?,?,?,?,?,?,?,?)"""

        private const val DELETE_ALL_OLDER_THAN =
            """DELETE FROM user_suggestions WHERE inserted_time < NOW() - INTERVAL '<days> days'"""

        private const val SELECT_LAST_BY_CHAT_ID = """
            SELECT * FROM user_suggestions 
            WHERE author_chat_id = ? 
            ORDER BY inserted_time DESC LIMIT 1
        """

        private const val SELECT_BY_CHAT_AND_MESSAGE_ID =
            """SELECT * FROM user_suggestions WHERE <user>_chat_id = ? AND <user>_message_id = ?"""

        private const val SELECT_BY_STATUS =
            """SELECT * FROM user_suggestions WHERE status = ? ORDER BY inserted_time"""

        private const val DELETE_BY_CHAT_AND_MESSAGE_ID =
            """DELETE FROM user_suggestions WHERE <user>_chat_id = ? AND <user>_message_id = ?"""

        private const val UPDATE_BY_CHAT_AND_MESSAGE_ID = """
        UPDATE user_suggestions SET 
            editor_message_id = ?,
            editor_chat_id = ?,
            editor_comment = ?,
            status = ?,
            post_text = ?,
            image_id = ?
        WHERE <user>_chat_id = ? AND <user>_message_id = ?
        """
    }

}