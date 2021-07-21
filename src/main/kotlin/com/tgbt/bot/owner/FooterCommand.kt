package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.settings.Setting
import com.tgbt.telegram.output.TgTextOutput


object FooterCommand : BotCommand {
    override val command = "/footer "

    override suspend fun MessageContext.handle(): Unit = with(bot) {
        val value = messageText.removePrefix(command)
        settings[Setting.FOOTER_MD] = value
        val md = "Footer '$value' would be added at the end of each message with one empty line delimiter"
        tgMessageSender.sendChatMessage(chatId, TgTextOutput(md), message.id)
    }
}