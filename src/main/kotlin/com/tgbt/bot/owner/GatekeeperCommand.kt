package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.settings.Setting
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.output.TgTextOutput


object GatekeeperCommand : BotCommand {
    override val command = "/gatekeeper "

    override suspend fun MessageContext.handle() {
        val value = messageText.removePrefix(command)
        Setting.GATEKEEPER.save(value)
        val md = "Gatekeeper '$value' would be shown as ban-related contact"
        TelegramClient.sendChatMessage(chatId, TgTextOutput(md), message.id)
    }
}