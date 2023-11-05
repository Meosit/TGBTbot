package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.settings.Setting
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.output.TgTextOutput

object SendFreezeStatusCommand : BotCommand {
    override val command = "/send_freeze_status"

    override suspend fun MessageContext.handle() {
        when (val value = messageText.removePrefix(command).trim()) {
            "" -> TelegramClient.sendChatMessage(chatId, TgTextOutput("Argument expected"), message.id)
            "true", "false" -> {
                Setting.SEND_FREEZE_STATUS.save(value)
                val markdownText = if (value == "true")
                    "Enabled sending additional direct message to owners in case of schedule freeze" else "Disabled sending additional direct message to owners in case of schedule freeze"
                TelegramClient.sendChatMessage(chatId, TgTextOutput(markdownText), message.id)
            }
            else -> {
                TelegramClient.sendChatMessage(chatId, TgTextOutput("Invalid argument '$value'"), message.id)
            }
        }
    }
}