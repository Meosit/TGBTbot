package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.misc.loadResourceAsString
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.output.TgTextOutput

object OwnerHelpCommand: BotCommand {
    override val command: String = "/help"
    private val helpMessage = loadResourceAsString("help.owner.md")

    override suspend fun MessageContext.handle() {
        TelegramClient.sendChatMessage(chatId, TgTextOutput(helpMessage))
    }
}