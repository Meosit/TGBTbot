package com.tgbt.telegram

import by.mksn.inintobot.telegram.User
import kotlinx.serialization.Serializable

@Serializable
data class CallbackQuery(
    val id: String,
    val from: User,
    val message: Message? = null,
    val data: String
)