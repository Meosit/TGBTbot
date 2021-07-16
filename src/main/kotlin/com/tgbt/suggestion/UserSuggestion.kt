package com.tgbt.suggestion

import java.sql.Timestamp
import java.time.Instant

data class UserSuggestion(
    val authorMessageId: Long,
    val authorChatId: Long,
    val authorName: String,
    val editorMessageId: Long? = null,
    val editorChatId: Long? = null,
    val status: SuggestionStatus = SuggestionStatus.PENDING_USER_EDIT,
    val postText: String,
    val imageId: String? = null,
    val insertedTime: Timestamp = Timestamp.from(Instant.now())
)
