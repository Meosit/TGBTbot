package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.settings.Setting
import com.tgbt.telegram.output.TgTextOutput

object PostCountCommand : BotCommand {
    override val command = "/count "

    override suspend fun MessageContext.handle(): Unit = with(bot) {
        when (val value = messageText.removePrefix(command)) {
            "" -> tgMessageSender.sendChatMessage(chatId, TgTextOutput("Argument expected"), message.id)
            else -> {
                val markdownText = if (value.toIntOrNull() != null) {
                    if (value.toInt() in 1..1000) {
                        settings[Setting.POST_COUNT_TO_LOAD] = value
                        "Every time $value posts will be loaded from VK"
                    } else {
                        "Value must be between 1 and 1000"
                    }
                } else {
                    "Integer value expected, got '$value'"
                }
                tgMessageSender.sendChatMessage(chatId, TgTextOutput(markdownText), message.id)
            }
        }
    }
}