package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.settings.Setting
import com.tgbt.telegram.output.TgTextOutput


object CheckPeriodCommand : BotCommand {
    override val command = "/check "

    override suspend fun MessageContext.handle() = with(bot) {
        when (val value = messageText.removePrefix(command)) {
            "" -> tgMessageSender.sendChatMessage(chatId, TgTextOutput("Argument expected"), message.id)
            else -> {
                val markdownText = if (value.toIntOrNull() != null) {
                    if (value.toInt() in 5..29) {
                        settings[Setting.CHECK_PERIOD_MINUTES] = value
                        "VK would be checked every $value minutes"
                    } else {
                        "Value must be between 5 and 29"
                    }
                } else {
                    "Integer value expected, got '$value'"
                }
                tgMessageSender.sendChatMessage(chatId, TgTextOutput(markdownText), message.id)
            }
        }
    }
}