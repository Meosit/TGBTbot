package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.settings.Setting
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.output.TgTextOutput


object SuggestionDelayCommand : BotCommand {
    override val command = "/suggestion_delay "

    override suspend fun MessageContext.handle() {
        when (val value = messageText.removePrefix(command)) {
            "" -> TelegramClient.sendChatMessage(chatId, TgTextOutput("Argument expected"), message.id)
            else -> {
                val markdownText = if (value.toIntOrNull() != null) {
                    Setting.USER_SUGGESTION_DELAY_MINUTES.save(value)
                    "Now users can post one suggestion per $value minutes"
                } else {
                    "Integer value expected, got '$value'"
                }
                TelegramClient.sendChatMessage(chatId, TgTextOutput(markdownText), message.id)
            }
        }
    }
}