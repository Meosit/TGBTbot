package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.settings.Setting
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.output.TgTextOutput

object RetentionPeriodCommand : BotCommand {
    override val command = "/retention "

    override suspend fun MessageContext.handle() {
        when (val value = messageText.removePrefix(command)) {
            "" -> TelegramClient.sendChatMessage(chatId, TgTextOutput("Argument expected"), message.id)
            else -> {
                val markdownText = if (value.toIntOrNull() != null) {
                    if (value.toInt() > 1) {
                        Setting.RETENTION_PERIOD_DAYS.save(value)
                        "Posts older than $value will be deleted from the database"
                    } else {
                        "Retention period must be set at minimum 1 day"
                    }
                } else {
                    "Integer value expected, got '$value'"
                }
                TelegramClient.sendChatMessage(chatId, TgTextOutput(markdownText), message.id)
            }
        }
    }
}