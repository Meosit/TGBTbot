package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.settings.Setting
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.output.TgTextOutput

object ForwardingCommand: BotCommand {
    override val command = "/forwarding"

    override suspend fun MessageContext.handle() {
        when (val value = messageText.removePrefix(command).trim()) {
            "" -> TelegramClient.sendChatMessage(chatId, TgTextOutput("Argument expected"), message.id)
            "true", "false" -> {
                Setting.FORWARDING_ENABLED.save(value)
                val markdownText = if (value == "true")
                    "VK -> TG Post forwarding enabled" else "VK -> TG Post forwarding disabled"
                TelegramClient.sendChatMessage(chatId, TgTextOutput(markdownText), message.id)
            }
            else -> {
                TelegramClient.sendChatMessage(chatId, TgTextOutput("Invalid argument '$value'"), message.id)
            }
        }
    }
}