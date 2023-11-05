package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.suggestion.SuggestionStore
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.output.TgTextOutput

object SuggestionsCleanCommand: BotCommand {
    override val command = "/clean_old_suggestions"

    override suspend fun MessageContext.handle() {
        when (val value = messageText.removePrefix(command).trim()) {
            "" -> TelegramClient.sendChatMessage(chatId, TgTextOutput("Argument expected"), message.id)
            else -> {
                val markdownText = if (value.toIntOrNull() != null) {
                    val removed = SuggestionStore.removeAllOlderThan(value.toInt())
                    "Removed $removed posts older than $value days"
                } else {
                    "Integer value expected, got '$value'"
                }
                TelegramClient.sendChatMessage(chatId, TgTextOutput(markdownText), message.id)
            }
        }
    }
}