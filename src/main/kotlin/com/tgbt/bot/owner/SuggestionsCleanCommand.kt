package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.telegram.output.TgTextOutput

object SuggestionsCleanCommand: BotCommand {
    override val command = "/cleansuggestions"

    override suspend fun MessageContext.handle(): Unit = with(bot) {
        val count = suggestionStore.removeAll()
        ownerIds.forEach { tgMessageSender.sendChatMessage(it, TgTextOutput("Removed all $count suggestions")) }
    }
}