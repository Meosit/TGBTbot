package com.tgbt.bot

import com.tgbt.post.PostStore
import com.tgbt.settings.Settings
import com.tgbt.telegram.Message
import com.tgbt.telegram.TgMessageSender
import kotlinx.serialization.json.Json

data class MessageContext(
    val postStore: PostStore,
    val settings: Settings,
    val json: Json,
    val tgMessageSender: TgMessageSender,
    val message: String,
    val chatId: String,
    val messageId: Long
) {
    constructor(
        postStore: PostStore,
        settings: Settings,
        json: Json,
        tgMessageSender: TgMessageSender,
        message: Message
    ) :
            this(
                postStore,
                settings,
                json,
                tgMessageSender,
                message.text ?: "",
                message.chat.id.toString(),
                message.messageId
            )
}