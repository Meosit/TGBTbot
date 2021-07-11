package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.settings.Setting
import com.tgbt.telegram.output.TgTextOutput

class ForwardingCommand: BotCommand {
    override val command = "/forwarding "

    override suspend fun MessageContext.handle() = with(bot) {
        when (val value = message.removePrefix(command)) {
            "" -> tgMessageSender.sendChatMessage(chatId, TgTextOutput("Argument expected"), messageId)
            "true", "false" -> {
                settings[Setting.FORWARDING_ENABLED] = value
                val markdownText = if (value == "true")
                    "VK -> TG Post forwarding enabled" else "VK -> TG Post forwarding disabled"
                tgMessageSender.sendChatMessage(chatId, TgTextOutput(markdownText), messageId)
            }
            else -> {
                tgMessageSender.sendChatMessage(chatId, TgTextOutput("Invalid argument '$value'"), messageId)
            }
        }
    }
}