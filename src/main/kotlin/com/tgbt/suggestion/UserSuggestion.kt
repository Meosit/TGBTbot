package com.tgbt.suggestion

import com.tgbt.misc.trimToLength
import java.sql.Timestamp
import java.time.Instant

data class UserSuggestion(
    val authorMessageId: Long,
    val authorChatId: Long,
    val authorName: String,
    val editorMessageId: Long? = null,
    val editorChatId: Long? = null,
    val editorComment: String = "",
    val status: SuggestionStatus = SuggestionStatus.PENDING_USER_EDIT,
    val postText: String,
    val imageId: String? = null,
    val insertedTime: Timestamp = Timestamp.from(Instant.now()),
    val scheduleTime: Timestamp? = null
)

fun UserSuggestion.authorReference(anonymous: Boolean) =
    "предложено${if (anonymous) "" else " $authorName"} через @tgbtbot"

fun UserSuggestion.postTextTeaser() =
    postText.trimToLength(20, "…").replace('\n', ' ')