package com.tgbt.bot.user

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.bot.user.UserMessages.startMessage
import com.tgbt.telegram.output.TgTextOutput

object UserStartCommand: BotCommand {
    override val command: String = "/start"

    override suspend fun MessageContext.handle() {
        bot.tgMessageSender
            .sendChatMessage(chatId, TgTextOutput(startMessage))
    }
}