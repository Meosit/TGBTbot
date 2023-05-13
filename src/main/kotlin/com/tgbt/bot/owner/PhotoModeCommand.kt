package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.settings.Setting
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.output.TgTextOutput

object PhotoModeCommand : BotCommand {
    override val command = "/photo_mode "

    override suspend fun MessageContext.handle() {
        when (val value = messageText.removePrefix(command)) {
            "" -> TelegramClient.sendChatMessage(chatId, TgTextOutput("Argument expected"), message.id)
            "true", "false" -> {
                Setting.USE_PHOTO_MODE.save(value)
                val markdownText = if (value == "true")
                    "Posts with image and text length up to 1024 chars to be sent via image with caption"
                else "Posts with image and text length up to 1024 chars to be sent via message with imaage link"
                TelegramClient.sendChatMessage(chatId, TgTextOutput(markdownText), message.id)
            }
            else -> {
                TelegramClient.sendChatMessage(chatId, TgTextOutput("Invalid argument '$value'"), message.id)
            }
        }
    }
}