package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.settings.Setting
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.output.TgTextOutput


object FooterCommand : BotCommand {
    override val command = "/footer "

    override suspend fun MessageContext.handle() {
        val value = messageText.removePrefix(command)
        Setting.FOOTER_MD.save(value)
        val md = "Footer '$value' would be added at the end of each message with one empty line delimiter"
        TelegramClient.sendChatMessage(chatId, TgTextOutput(md), message.id)
    }
}