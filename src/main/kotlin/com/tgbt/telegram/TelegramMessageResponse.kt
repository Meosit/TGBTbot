package com.tgbt.telegram

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TelegramMessageResponse(
    val ok: Boolean,
    val result: Message? = null,
    @SerialName("error_code") val errorCode: Int? = null,
    val description: String? = null
)