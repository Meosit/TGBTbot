package com.tgbt.telegram

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Message(
    @SerialName("message_id") val id: Long,
    val text: String? = null,
    val from: User? = null,
    val date: Int,
    val chat: Chat,
    @SerialName("reply_to_message") val replyToMessage: Message? = null,
    val photo: List<PhotoSize>? = null,
    val caption: String? = null
)


val Message.imageId: String? get() = photo?.firstOrNull()?.fileId
val Message.anyText: String? get() = text ?: caption
val Message.verboseUserName: String get() = from?.verboseUserName ?: chat.verboseUserName
