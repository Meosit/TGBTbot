package com.tgbt.bot.owner

import com.tgbt.bot.MessageContext
import com.tgbt.settings.Setting
import com.tgbt.telegram.output.TgTextOutput

const val PHOTO_MODE_COMMAND = "/photomode "

suspend fun MessageContext.handlePhotoModeCommand() {
    when (val value = message.removePrefix(PHOTO_MODE_COMMAND)) {
        "" -> tgMessageSender.sendChatMessage(chatId, TgTextOutput("Argument expected"), messageId)
        "true", "false" -> {
            settings[Setting.USE_PHOTO_MODE] = value
            val markdownText = if (value == "true")
                "Posts with image and text length up to 1024 chars to be sent via image with caption"
            else "Posts with image and text length up to 1024 chars to be sent via message with imaage link"
            tgMessageSender.sendChatMessage(chatId, TgTextOutput(markdownText), messageId)
        }
        else -> {
            tgMessageSender.sendChatMessage(chatId, TgTextOutput("Invalid argument '$value'"), messageId)
        }
    }
}