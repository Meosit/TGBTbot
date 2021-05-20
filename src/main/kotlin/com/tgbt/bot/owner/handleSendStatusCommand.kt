package com.tgbt.bot.owner

import com.tgbt.bot.MessageContext
import com.tgbt.settings.Setting
import com.tgbt.telegram.output.TgTextOutput


const val SEND_STATUS_COMMAND = "/send_status "

suspend fun MessageContext.handleSendStatusCommand() {
    when (val value = message.removePrefix(SEND_STATUS_COMMAND)) {
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