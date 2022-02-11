package com.tgbt.bot.user

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.bot.user.UserMessages.helpMessage
import com.tgbt.settings.Setting
import com.tgbt.telegram.output.TgTextOutput

object UserHelpCommand: BotCommand {
    override val command: String = "/help"

    override suspend fun MessageContext.handle() {
        bot.tgMessageSender
            .sendChatMessage(chatId, TgTextOutput(helpMessage
                .format(
                    bot.settings.str(Setting.USER_EDIT_TIME_MINUTES),
                    bot.settings.str(Setting.USER_SUGGESTION_DELAY_MINUTES),
                    bot.settings.str(Setting.GATEKEEPER)
                )))
    }
}