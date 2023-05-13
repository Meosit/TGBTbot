package com.tgbt.bot.editor

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.misc.loadResourceAsString
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.output.TgTextOutput

object EditorHelpCommand : BotCommand {
    override val command: String = "/help"
    private val helpMessage = loadResourceAsString("editor/help.md")

    override suspend fun MessageContext.handle() {
        TelegramClient.sendChatMessage(chatId, TgTextOutput(helpMessage))
    }

}
