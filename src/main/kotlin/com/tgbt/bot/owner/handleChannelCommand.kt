package com.tgbt.bot.owner

import com.tgbt.bot.MessageContext
import com.tgbt.settings.Setting
import com.tgbt.telegram.output.TgTextOutput


const val CHANNEL_COMMAND = "/channel "

suspend fun MessageContext.handleChannelCommand() {
    when (val value = message.removePrefix(CHANNEL_COMMAND)) {
        "" -> tgMessageSender.sendChatMessage(chatId, TgTextOutput("Argument expected"), messageId)
        else -> {
            settings[Setting.TARGET_CHANNEL] = value
            val md = "Target channel is set to '$value' (please ensure that it's ID or username which starts from '@')"
            tgMessageSender.sendChatMessage(chatId, TgTextOutput(md), messageId)
        }
    }
}