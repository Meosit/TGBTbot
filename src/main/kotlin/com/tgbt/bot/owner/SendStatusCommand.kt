package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.settings.Setting
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.output.TgTextOutput

object SendStatusCommand : BotCommand {
    override val command = "/send_status "

    override suspend fun MessageContext.handle() {
        when (val value = messageText.removePrefix(command)) {
            "" -> TelegramClient.sendChatMessage(chatId, TgTextOutput("Argument expected"), message.id)
            "true", "false" -> {
                Setting.SEND_STATUS.save(value)
                val markdownText = if (value == "true")
                    "Enabled sending post forward status each time" else "Enabled sending post forward status each time"
                TelegramClient.sendChatMessage(chatId, TgTextOutput(markdownText), message.id)
            }
            else -> {
                TelegramClient.sendChatMessage(chatId, TgTextOutput("Invalid argument '$value'"), message.id)
            }
        }
    }
}