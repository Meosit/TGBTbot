package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.settings.Setting
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.output.TgTextOutput


object EditorsChatCommand: BotCommand {
    override val command = "/editors_chat"

    override suspend fun MessageContext.handle() {
        when (val value = messageText.removePrefix(command).trim()) {
            "" -> TelegramClient.sendChatMessage(chatId, TgTextOutput("Argument expected"), message.id)
            else -> {
                val md = if (value.toLongOrNull() != null) {
                    Setting.EDITOR_CHAT_ID.save(value)
                    "Editors chat ID is set to '$value' (please ensure that it's a correct ID and bot is admin here)"
                } else {
                    "Editors chat ID must be numeric, got '$value'"
                }
                TelegramClient.sendChatMessage(chatId, TgTextOutput(md), message.id)
            }
        }
    }
}