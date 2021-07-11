package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.settings.Setting
import com.tgbt.telegram.output.TgTextOutput

class RetentionPeriodCommand : BotCommand {
    override val command = "/retention "

    override suspend fun MessageContext.handle() = with(bot) {
        when (val value = message.removePrefix(command)) {
            "" -> tgMessageSender.sendChatMessage(chatId, TgTextOutput("Argument expected"), messageId)
            else -> {
                val markdownText = if (value.toIntOrNull() != null) {
                    if (value.toInt() > 1) {
                        settings[Setting.RETENTION_PERIOD_DAYS] = value
                        "Posts older than $value will be deleted from the database"
                    } else {
                        "Retention period must be set at minimum 1 day"
                    }
                } else {
                    "Integer value expected, got '$value'"
                }
                tgMessageSender.sendChatMessage(chatId, TgTextOutput(markdownText), messageId)
            }
        }
    }
}