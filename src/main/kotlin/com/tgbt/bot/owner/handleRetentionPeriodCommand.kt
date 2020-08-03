package com.tgbt.bot.owner

import com.tgbt.bot.MessageContext
import com.tgbt.settings.Setting
import com.tgbt.telegram.output.TgTextOutput


const val RETENTION_PERIOD_COMMAND = "/retention "

suspend fun MessageContext.handleRetentionPeriodCommand() {
    when (val value = message.removePrefix(RETENTION_PERIOD_COMMAND)) {
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