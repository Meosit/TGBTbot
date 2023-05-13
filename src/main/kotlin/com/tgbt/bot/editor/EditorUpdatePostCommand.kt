package com.tgbt.bot.editor

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.bot.editor.button.BanMenuHandler
import com.tgbt.bot.editor.button.ModifyMenuHandler
import com.tgbt.bot.editor.button.RejectMenuHandler
import com.tgbt.misc.isImageUrl
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.api.simpleRef
import com.tgbt.telegram.output.TgTextOutput

object EditorUpdatePostCommand : BotCommand {
    override val command: String
        get() = throw IllegalStateException("This command can be triggered by multiple prefixes")

    private const val REJECT_COMMAND = "/reject"
    private const val BAN_COMMAND = "/ban"

    override fun canHandle(message: String): Boolean {
        return message.startsWith(REJECT_COMMAND) || message.startsWith(BAN_COMMAND) || message.trim().isImageUrl()
    }

    override suspend fun MessageContext.handle() {
        if (replyMessage == null || replyMessage.from?.isBot != true) {
            return
        }
        when {
            messageText.startsWith(REJECT_COMMAND) -> {
                when (val comment = messageText.removePrefix(REJECT_COMMAND).trim()) {
                    "" -> TelegramClient.sendChatMessage(chatId, TgTextOutput("Зачем использовать эту команду без комментария?"), message.id)
                    else -> RejectMenuHandler.handle(replyMessage, message.from.simpleRef, comment)
                }
            }
            messageText.startsWith(BAN_COMMAND) -> {
                when (val comment = messageText.removePrefix(BAN_COMMAND).trim()) {
                    "" -> TelegramClient.sendChatMessage(chatId, TgTextOutput("Нужно указать причину блокировки"), message.id)
                    else -> BanMenuHandler.handle(replyMessage, message.from.simpleRef, comment)
                }
            }
            messageText.trim().isImageUrl() ->
                ModifyMenuHandler.modifyWithCustomPic(replyMessage, message.from.simpleRef, messageText.trim())
        }
    }
}
