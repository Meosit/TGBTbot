package com.tgbt.bot.user

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.bot.user.UserMessages.startMessage
import com.tgbt.settings.Setting
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.output.TgTextOutput

object UserStartCommand: BotCommand {
    override val command: String = "/start"

    override suspend fun MessageContext.handle() {
        TelegramClient.sendChatMessage(chatId, TgTextOutput(startMessage.format(Setting.GATEKEEPER.str())))
    }
}