package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.settings.Setting
import com.tgbt.telegram.output.TgTextOutput


object SuggestionPoolingCommand : BotCommand {
    override val command = "/suggestionpooling "

    override suspend fun MessageContext.handle(): Unit = with(bot) {
        when (val value = messageText.removePrefix(command)) {
            "" -> tgMessageSender.sendChatMessage(chatId, TgTextOutput("Argument expected"), message.id)
            else -> {
                val markdownText = if (value.toIntOrNull() != null) {
                    settings[Setting.SUGGESTION_POLLING_DELAY_MINUTES] = value
                    "New suggestions would be checked every $value minutes"
                } else {
                    "Integer value expected, got '$value'"
                }
                tgMessageSender.sendChatMessage(chatId, TgTextOutput(markdownText), message.id)
            }
        }
    }
}