package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.forwardVkPosts
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.output.TgTextOutput

object ForceForwardCommand : BotCommand {
    override val command = "/force_forward"

    override suspend fun MessageContext.handle() {
        forwardVkPosts(forcedByOwner = true)
        TelegramClient.sendChatMessage(chatId, TgTextOutput("Forward check finished"))
    }

}
