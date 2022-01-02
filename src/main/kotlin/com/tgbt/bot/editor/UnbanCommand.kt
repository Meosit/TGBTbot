package com.tgbt.bot.editor

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.bot.user.UserMessages
import com.tgbt.misc.escapeMarkdown
import com.tgbt.telegram.output.TgTextOutput

object UnbanCommand : BotCommand {
    override val command: String = "/unban "

    override suspend fun MessageContext.handle(): Unit = with(bot) {
        when (val nameOrChatId = messageText.removePrefix(command).trim()) {
            "" -> tgMessageSender.sendChatMessage(chatId, TgTextOutput("Укажи @никнейм или chat ID для разблокировки. Chat ID пользователь должен узнать сам, например через @myidbot"), message.id)
            else -> {
                val ban = banStore.findByChatIdOrName(nameOrChatId)
                if (ban != null) {
                    val actuallyUnbanned = banStore.remove(ban.authorChatId)
                    if (actuallyUnbanned) {
                        tgMessageSender.sendChatMessage(chatId, TgTextOutput("Пользователь ${ban.authorName.escapeMarkdown()} разблокирован"))
                        tgMessageSender.sendChatMessage(ban.authorChatId.toString(), TgTextOutput(UserMessages.unbannedMessage))
                    }
                } else {
                    tgMessageSender.sendChatMessage(chatId, TgTextOutput("По запросу '${nameOrChatId.escapeMarkdown()}' в списке заблокированных никого не найдено"), message.id)
                }
            }
        }
    }

}
