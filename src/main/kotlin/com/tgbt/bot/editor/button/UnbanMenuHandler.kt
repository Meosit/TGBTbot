package com.tgbt.bot.editor.button

import com.tgbt.ban.BanStore
import com.tgbt.bot.button.CallbackButtonHandler
import com.tgbt.bot.button.CallbackNotificationText
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.api.*
import com.tgbt.telegram.output.TgTextOutput
import org.slf4j.LoggerFactory

object UnbanMenuHandler : CallbackButtonHandler("UNBAN", "") {

    fun rootKeyboard(chatId: Long) = InlineKeyboardMarkup(
        unbanOptions.map { (key, comment) -> listOf(InlineKeyboardButton(comment, callbackData("$key|$chatId"))) }
    )

    private val logger = LoggerFactory.getLogger(this::class.simpleName)

    private const val rejectOption = "reject"
    private const val acceptOption = "accept"

    private const val FINISH_PAYLOAD = "done"

    private val unbanOptions = mapOf(
        rejectOption to "⛔\uFE0F Отклонить ⛔\uFE0F",
        acceptOption to "✅ Разбанить ✅",
    )

    override fun isValidPayload(payload: String): Boolean =
        payload == FINISH_PAYLOAD || payload.takeWhile { it != '|' } in unbanOptions

    private suspend fun renderFinishKeyboard(
        message: Message,
        label: String = "\uD83D\uDD12 Бан не найден \uD83D\uDD12"
    ): CallbackNotificationText {
        TelegramClient.editChatMessageKeyboard(
            message.chat.id.toString(),
            message.id,
            InlineKeyboardButton(label, callbackData(FINISH_PAYLOAD)).toMarkup().toJson()
        )
        return label
    }

    override suspend fun handleButtonAction(
        message: Message,
        pressedBy: String,
        validPayload: String,
    ): CallbackNotificationText {
        if (validPayload == FINISH_PAYLOAD)
            return null
        val (option, chatId) = validPayload.split("|")
        val ban = BanStore.findByChatId(chatId.toLongOrNull() ?: 0L)
            ?: return renderFinishKeyboard(message)
        return when (option) {
            rejectOption -> {
                TelegramClient.sendChatMessage(ban.authorChatId.toString(), TgTextOutput("\uD83D\uDEAB Заявка на разбан была отклонена"))
                renderFinishKeyboard(message, "\uD83D\uDEAB Заявка отклонена \uD83D\uDEAB")
            }
            acceptOption -> {
                BanStore.remove(ban.authorChatId)
                TelegramClient.sendChatMessage(ban.authorChatId.toString(), TgTextOutput("✅ Возможность предлагать посты была возвращена редакторами"))
                renderFinishKeyboard(message, "✅ Разблокирован ✅")
            }
            else -> renderFinishKeyboard(message)
        }
    }

    override suspend fun createHandlerKeyboard(message: Message, pressedBy: String): InlineKeyboardMarkup {
        logger.error("createHandlerKeyboard was called by $pressedBy with message: $message")
        throw IllegalStateException("This method is not available for unbans")
    }
}