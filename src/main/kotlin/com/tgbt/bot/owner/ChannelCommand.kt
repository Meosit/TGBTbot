package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.settings.Setting
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.output.TgTextOutput


object ChannelCommand: BotCommand {
    override val command = "/channel"

    override suspend fun MessageContext.handle() {
        when (val value = messageText.removePrefix(command).trim()) {
            "" -> TelegramClient.sendChatMessage(chatId, TgTextOutput("Argument expected"), message.id)
            else -> {
                Setting.TARGET_CHANNEL.save(value)
                val md = "Target channel is set to '$value' (please ensure that it's ID or username which starts from '@')"
                TelegramClient.sendChatMessage(chatId, TgTextOutput(md), message.id)
            }
        }
    }
}