package com.tgbt.telegram.api

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
    val caption: String? = null,
    @SerialName("entities") val entities: List<MessageEntity> = emptyList(),
    @SerialName("caption_entities") val captionEntities: List<MessageEntity> = emptyList(),
    @SerialName("reply_markup") val replyMarkup: InlineKeyboardMarkup? = null
)

@Serializable
data class MessageEntity(
    val type: String,
    val offset: Int,
    val length: Int
)

val Message.imageId: String? get() = photo?.firstOrNull()?.fileId
val Message.anyText: String? get() = text ?: caption
val Message.verboseUserName: String get() = from?.verboseUserName ?: chat.verboseUserName

val Message.verboseLogReference: String get() = "[${this.chat.title ?: ("${this.chat.firstName} ${this.chat.lastName}")};${this.chat.id}](${this.from.simpleRef})"

fun Message.textWithFixedCommand(): String {
    val text = anyText ?: ""
    for (entity in (entities + captionEntities)) {
        if (entity.type == "command") {
            val command = text.substring(entity.offset, entity.offset + entity.length)
            val index = command.indexOf('@')
            return if (index >= 0) text.replace(command, command.removeRange(index, command.length)) else text
        }
    }
    return text
}