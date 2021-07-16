package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.forwardVkPosts
import com.tgbt.telegram.output.TgTextOutput

object ForceForwardCommand : BotCommand {
    override val command = "/forceforward"

    override suspend fun MessageContext.handle() = with(bot) {
        forwardVkPosts(bot, forcedByOwner = true)
        ownerIds.forEach { tgMessageSender.sendChatMessage(it, TgTextOutput("Forward check finished")) }
    }

}
