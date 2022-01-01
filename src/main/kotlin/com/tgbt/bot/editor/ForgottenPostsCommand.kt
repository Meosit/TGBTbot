package com.tgbt.bot.editor

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.notifyAboutForgottenSuggestions
import com.tgbt.settings.Setting
import com.tgbt.telegram.output.TgTextOutput

object ForgottenPostsCommand : BotCommand {
    override val command: String = "/forgotten"

    override suspend fun MessageContext.handle() {
        val threshold = messageText.removePrefix(command).trim().toIntOrNull()?.takeIf { it in 0..1000 } ?: 0
        bot.notifyAboutForgottenSuggestions(force = true, createdBeforeHours = threshold)
        val targetChat = bot.settings.str(Setting.EDITOR_CHAT_ID)
        bot.tgMessageSender.sendChatMessage(targetChat, TgTextOutput("Проверка на забытые посты завершена"))
    }

}
