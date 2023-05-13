package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.misc.escapeMarkdown
import com.tgbt.settings.Setting
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.output.TgTextOutput

object SettingsCommand : BotCommand {
    override val command: String = "/settings"

    override suspend fun MessageContext.handle() {
        TelegramClient.sendChatMessage(
            chatId,
            TgTextOutput(Setting.values().joinToString("\n") { "${it.name}: ${it.str()}".escapeMarkdown() }),
            disableLinkPreview = true
        )
    }
}