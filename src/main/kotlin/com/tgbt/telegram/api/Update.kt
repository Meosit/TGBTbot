package com.tgbt.telegram.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Update(
    @SerialName("update_id") val updateId: Long,
    val message: Message? = null,
    @SerialName("edited_message") val editedMessage: Message? = null,
    @SerialName("callback_query") val callbackQuery: CallbackQuery? = null
)

