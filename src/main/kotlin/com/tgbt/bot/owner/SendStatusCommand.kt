package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.settings.Setting
import com.tgbt.telegram.output.TgTextOutput

class SendStatusCommand : BotCommand {
    override val command = "/sendstatus "

    override suspend fun MessageContext.handle() = with(bot) {
        when (val value = message.removePrefix(command)) {
            "" -> tgMessageSender.sendChatMessage(chatId, TgTextOutput("Argument expected"), messageId)
            "true", "false" -> {
                settings[Setting.SEND_STATUS] = value
                val markdownText = if (value == "true")
                    "Enabled sending post forward status each time" else "Enabled sending post forward status each time"
                tgMessageSender.sendChatMessage(chatId, TgTextOutput(markdownText), messageId)
            }
            else -> {
                tgMessageSender.sendChatMessage(chatId, TgTextOutput("Invalid argument '$value'"), messageId)
            }
        }
    }
}