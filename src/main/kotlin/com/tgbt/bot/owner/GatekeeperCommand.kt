package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.settings.Setting
import com.tgbt.telegram.output.TgTextOutput


object GatekeeperCommand : BotCommand {
    override val command = "/gatekeeper "

    override suspend fun MessageContext.handle(): Unit = with(bot) {
        val value = messageText.removePrefix(command)
        settings[Setting.GATEKEEPER] = value
        val md = "Gatekeeper '$value' would be shown as ban-related contact"
        tgMessageSender.sendChatMessage(chatId, TgTextOutput(md), message.id)
    }
}