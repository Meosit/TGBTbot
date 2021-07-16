package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.settings.Setting
import com.tgbt.telegram.output.TgTextOutput


object ChannelCommand: BotCommand {
    override val command = "/channel "

    override suspend fun MessageContext.handle() = with(bot) {
        when (val value = messageText.removePrefix(command)) {
            "" -> tgMessageSender.sendChatMessage(chatId, TgTextOutput("Argument expected"), message.id)
            else -> {
                settings[Setting.TARGET_CHANNEL] = value
                val md = "Target channel is set to '$value' (please ensure that it's ID or username which starts from '@')"
                tgMessageSender.sendChatMessage(chatId, TgTextOutput(md), message.id)
            }
        }
    }
}