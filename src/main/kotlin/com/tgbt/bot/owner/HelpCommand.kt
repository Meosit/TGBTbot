package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.misc.loadResourceAsString
import com.tgbt.telegram.output.TgTextOutput

class HelpCommand: BotCommand {
    override val command: String = "/help"
    override suspend fun MessageContext.handle() = bot.tgMessageSender
        .sendChatMessage(chatId, TgTextOutput(loadResourceAsString("help.owner.md")))
}