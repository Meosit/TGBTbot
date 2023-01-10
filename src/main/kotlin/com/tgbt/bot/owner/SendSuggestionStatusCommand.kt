package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.settings.Setting
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.output.TgTextOutput

object SendSuggestionStatusCommand : BotCommand {
    override val command = "/send_suggestion_status "

    override suspend fun MessageContext.handle() {
        when (val value = messageText.removePrefix(command)) {
            "" -> TelegramClient.sendChatMessage(chatId, TgTextOutput("Argument expected"), message.id)
            "true", "false" -> {
                Setting.SEND_SUGGESTION_STATUS.save(value)
                val markdownText = if (value == "true")
                    "Enabled sending suggestion status each time" else "Enabled suggestion status each time"
                TelegramClient.sendChatMessage(chatId, TgTextOutput(markdownText), message.id)
            }
            else -> {
                TelegramClient.sendChatMessage(chatId, TgTextOutput("Invalid argument '$value'"), message.id)
            }
        }
    }
}