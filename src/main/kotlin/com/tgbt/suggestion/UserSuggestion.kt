package com.tgbt.suggestion

import com.tgbt.misc.teaserString
import com.tgbt.settings.Setting
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.max

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
    val scheduleTime: Timestamp? = null,
    val originallySentAsPhoto: Boolean = false
)

fun UserSuggestion.authorReference(anonymous: Boolean) =
    "предложено${if (anonymous) "" else " $authorName"} через @tgbtbot"

fun UserSuggestion.postTextTeaser() = postText.teaserString()

fun UserSuggestion.secondsSinceCreated() = ChronoUnit.SECONDS.between(insertedTime.toInstant(), Instant.now())

fun UserSuggestion.userEditSecondsRemaining() = if(editorMessageId != null) 0 else
    max(0, Setting.USER_EDIT_TIME_MINUTES.long() * 60 - ChronoUnit.SECONDS.between(insertedTime.toInstant(), Instant.now()))

fun UserSuggestion.userNewPostSecondsRemaining() = max(0, Setting.USER_SUGGESTION_DELAY_MINUTES.long() * 60 - secondsSinceCreated())

fun UserSuggestion.userCanEdit() = userEditSecondsRemaining() > 0

fun UserSuggestion.userCanAddNewPosts() = userNewPostSecondsRemaining() <= 0
