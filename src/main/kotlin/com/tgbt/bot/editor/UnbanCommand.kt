package com.tgbt.bot.editor

import com.tgbt.ban.BanStore
import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.bot.user.UserMessages
import com.tgbt.misc.escapeMarkdown
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.output.TgTextOutput

object UnbanCommand : BotCommand {
    override val command: String = "/unban "

    override suspend fun MessageContext.handle() {
        when (val nameOrChatId = messageText.removePrefix(command).trim()) {
            "" -> TelegramClient.sendChatMessage(chatId, TgTextOutput("Укажи @никнейм или chat ID для разблокировки. Chat ID пользователь должен узнать сам, например через @myidbot"), message.id)
            else -> {
                val ban = BanStore.findByChatIdOrName(nameOrChatId)
                if (ban != null) {
                    val actuallyUnbanned = BanStore.remove(ban.authorChatId)
                    if (actuallyUnbanned) {
                        TelegramClient.sendChatMessage(chatId, TgTextOutput("Пользователь ${ban.authorName.escapeMarkdown()} разблокирован"))
                        TelegramClient.sendChatMessage(ban.authorChatId.toString(), TgTextOutput(UserMessages.unbannedMessage))
                    }
                } else {
                    TelegramClient.sendChatMessage(chatId, TgTextOutput("По запросу '${nameOrChatId.escapeMarkdown()}' в списке заблокированных никого не найдено"), message.id)
                }
            }
        }
    }

}
