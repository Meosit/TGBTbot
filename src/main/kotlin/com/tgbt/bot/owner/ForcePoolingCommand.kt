package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.forwardSuggestions
import com.tgbt.telegram.output.TgTextOutput

object ForcePoolingCommand : BotCommand {
    override val command = "/force_pool_suggestions"

    override suspend fun MessageContext.handle(): Unit = with(bot) {
        forwardSuggestions(forcedByOwner = true)
        ownerIds.forEach { tgMessageSender.sendChatMessage(it, TgTextOutput("Suggestions pooling finished")) }
    }

}
