package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.settings.Setting
import com.tgbt.telegram.output.TgTextOutput

object SuggestionsDeletionCommand: BotCommand {
    override val command = "/suggestionsdeletion "

    override suspend fun MessageContext.handle(): Unit = with(bot) {
        when (val value = messageText.removePrefix(command)) {
            "" -> tgMessageSender.sendChatMessage(chatId, TgTextOutput("Argument expected"), message.id)
            "true", "false" -> {
                settings[Setting.SEND_PROMOTION_FEEDBACK] = value
                val markdownText = if (value == "true")
                    "Suggestions deletion feedback enabled" else "Suggestions deletion feedback disabled"
                tgMessageSender.sendChatMessage(chatId, TgTextOutput(markdownText), message.id)
            }
            else -> {
                tgMessageSender.sendChatMessage(chatId, TgTextOutput("Invalid argument '$value'"), message.id)
            }
        }
    }
}