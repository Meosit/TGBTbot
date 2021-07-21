package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.settings.Setting
import com.tgbt.telegram.output.TgTextOutput


object EditorsChatCommand: BotCommand {
    override val command = "/editorschat "

    override suspend fun MessageContext.handle(): Unit = with(bot) {
        when (val value = messageText.removePrefix(command)) {
            "" -> tgMessageSender.sendChatMessage(chatId, TgTextOutput("Argument expected"), message.id)
            else -> {
                val md = if (value.toLongOrNull() != null) {
                    settings[Setting.EDITOR_CHAT_ID] = value
                    "Editors chat ID is set to '$value' (please ensure that it's a correct ID and bot is admin here)"
                } else {
                    "Editors chat ID must be numeric, got '$value'"
                }
                tgMessageSender.sendChatMessage(chatId, TgTextOutput(md), message.id)
            }
        }
    }
}