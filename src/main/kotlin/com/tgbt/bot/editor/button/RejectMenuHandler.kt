package com.tgbt.bot.editor.button

import com.tgbt.BotJson
import com.tgbt.bot.BotCommand
import com.tgbt.bot.CallbackButtonHandler
import com.tgbt.bot.CallbackNotificationText
import com.tgbt.bot.MessageContext
import com.tgbt.bot.user.UserMessages
import com.tgbt.misc.escapeMarkdown
import com.tgbt.misc.simpleFormatTime
import com.tgbt.misc.trimToLength
import com.tgbt.suggestion.SuggestionStore
import com.tgbt.suggestion.postTextTeaser
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.api.InlineKeyboardButton
import com.tgbt.telegram.api.InlineKeyboardMarkup
import com.tgbt.telegram.api.Message
import com.tgbt.telegram.api.simpleRef
import com.tgbt.telegram.output.TgTextOutput
import org.slf4j.LoggerFactory
import java.time.Instant

object RejectMenuHandler: CallbackButtonHandler("EDIT", "REJECT"), BotCommand {

    private val logger = LoggerFactory.getLogger(this::class.simpleName)

    private const val silentRejectPayload = "silent"
    private val rejectComments = mapOf(
        "ddos" to "Хватит уже это форсить",
        "notfun" to "Не смешно же",
        "endfail" to "Концовка слита",
        "format" to "Оформи нормально и перезалей",
        "fck" to "Пошёл нахуй с такими бугуртами",
        "pic" to "Прикрепи картинку и перезалей",
        silentRejectPayload to null
    )

    override fun isValidPayload(payload: String): Boolean = payload in rejectComments

    override suspend fun handleButtonAction(
        message: Message,
        pressedBy: String,
        validPayload: String,
    ): CallbackNotificationText {
        return rejectCustomComment(message, pressedBy, rejectComments.getValue(validPayload))
    }

    private suspend fun rejectCustomComment(message: Message, pressedBy: String, rejectComment: String?): CallbackNotificationText {
        val suggestion = SuggestionStore.findByChatAndMessageId(message.chat.id, message.id, byAuthor = false)
        return if (suggestion?.editorChatId != null && suggestion.editorMessageId != null) {
            val actuallyDeleted = SuggestionStore.removeByChatAndMessageId(suggestion.editorChatId, suggestion.editorMessageId, byAuthor = false)
            if (actuallyDeleted) {
                val outputMessage = if (rejectComment != null) {
                    UserMessages.postDiscardedWithCommentMessage.format(suggestion.postTextTeaser().escapeMarkdown(), rejectComment.escapeMarkdown())
                } else {
                    UserMessages.postDiscardedMessage.format(suggestion.postTextTeaser().escapeMarkdown())
                }
                TelegramClient.sendChatMessage(suggestion.authorChatId.toString(), TgTextOutput(outputMessage))
                val commentPreview = if (rejectComment != null) " \uD83D\uDCAC $rejectComment" else ""
                logger.info("Editor ${message.from.simpleRef} rejected post '${suggestion.postTextTeaser()}' from ${suggestion.authorName} with comment '$rejectComment'")
                val label = "❌ Удалён $pressedBy в ${Instant.now().simpleFormatTime()}$commentPreview ❌"
                FinishedMenuHandler.finish(message, label.trimToLength(512, "…"))
            } else {
                FinishedMenuHandler.finish(message)
            }
        } else {
            FinishedMenuHandler.finish(message)
        }
    }

    override suspend fun renderNewMenu(message: Message, pressedBy: String): CallbackNotificationText {
        val keyboard = sequence {
            yield(listOf(InlineKeyboardButton("❌ Удалить без комментария ❌", callbackData(silentRejectPayload))))
            rejectComments
                .map { (key, comment) -> InlineKeyboardButton("❌ \uD83D\uDCAC \"$comment\" ❌", callbackData(key)) }
                .forEach { yield(listOf(it)) }
            yield(listOf(MainMenuHandler.BACK_TO_MAIN_BUTTON))
        }.toList().let { InlineKeyboardMarkup(it) }
        val keyboardJson = BotJson.encodeToString(InlineKeyboardMarkup.serializer(), keyboard)
        TelegramClient.editChatMessageKeyboard(message.chat.id.toString(), message.id, keyboardJson)
        return null
    }


    override val command: String = "/reject "

    override suspend fun MessageContext.handle() {
        if (replyMessage == null) return
        when (val comment = messageText.removePrefix(command).trim()) {
            "" -> TelegramClient.sendChatMessage(chatId, TgTextOutput("Зачем использовать эту команду без комментария?"), message.id)
            else -> rejectCustomComment(replyMessage, message.from.simpleRef, comment)
        }
    }
}