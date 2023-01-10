package com.tgbt.bot.user

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.bot.user.UserMessages.helpMessage
import com.tgbt.settings.Setting
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.output.TgTextOutput

object UserHelpCommand: BotCommand {
    override val command: String = "/help"

    override suspend fun MessageContext.handle() {
        TelegramClient
            .sendChatMessage(
                chatId, TgTextOutput(helpMessage
                .format(
                    Setting.USER_EDIT_TIME_MINUTES.str(),
                    Setting.USER_SUGGESTION_DELAY_MINUTES.str(),
                    Setting.GATEKEEPER.str()
                )))
    }
}