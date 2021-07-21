package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.forwardSuggestions
import com.tgbt.telegram.output.TgTextOutput

object ForcePoolingCommand : BotCommand {
    override val command = "/forcepoolsuggestions"

    override suspend fun MessageContext.handle(): Unit = with(bot) {
        forwardSuggestions()
        ownerIds.forEach { tgMessageSender.sendChatMessage(it, TgTextOutput("Suggestions pooling finished")) }
    }

}
