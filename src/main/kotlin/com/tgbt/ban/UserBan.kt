package com.tgbt.ban

import java.sql.Timestamp
import java.time.Instant

data class UserBan(
    val authorChatId: Long,
    val authorName: String,
    val reason: String,
    val postTeaser: String,
    val bannedBy: String,
    val insertedTime: Timestamp = Timestamp.from(Instant.now())
)
