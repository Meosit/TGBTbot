package com.tgbt.bot.editor.button

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.bot.button.CallbackButtonHandler
import com.tgbt.bot.button.CallbackNotificationText
import com.tgbt.bot.user.button.UserSuggestionMenuHandler
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
    private const val customCommentPayload = "custom"
    private val rejectComments = mapOf(
        silentRejectPayload to "❌ Удалить без комментария ❌",
        "ddos" to "Хватит уже это форсить",
        "notfun" to "Не смешно же",
        "endfail" to "Концовка слита",
        "format" to "Оформи нормально и перезалей",
        "fck" to "Пошёл нахуй с такими бугуртами",
        "pic" to "Прикрепи картинку и перезалей",
        customCommentPayload to "ℹ\uFE0F Свой комментарий ℹ\uFE0F",
    )

    override fun isValidPayload(payload: String): Boolean = payload in rejectComments

    override suspend fun handleButtonAction(
        message: Message,
        pressedBy: String,
        validPayload: String,
    ): CallbackNotificationText = when (validPayload) {
        customCommentPayload -> "Используй команду: /reject <комментарий>"
        silentRejectPayload -> rejectCustomComment(message, pressedBy, null)
        else -> rejectCustomComment(message, pressedBy, rejectComments.getValue(validPayload))
    }

    private suspend fun rejectCustomComment(message: Message, pressedBy: String, rejectComment: String?): CallbackNotificationText {
        val suggestion = SuggestionStore.findByMessage(message, byAuthor = false)
        return if (suggestion?.editorChatId != null && suggestion.editorMessageId != null) {
            val actuallyDeleted = SuggestionStore.removeByChatAndMessageId(suggestion.editorChatId, suggestion.editorMessageId, byAuthor = false)
            if (actuallyDeleted) {
                val outputMessage = "Пост '${suggestion.postTextTeaser().escapeMarkdown()}' был отклонен редакторами${rejectComment?.let { " с комментарием _${it.escapeMarkdown()}_" } ?: ""}"
                TelegramClient.sendChatMessage(suggestion.authorChatId.toString(), TgTextOutput(outputMessage))
                val commentPreview = if (rejectComment != null) " \uD83D\uDCAC $rejectComment" else ""
                logger.info("Editor $pressedBy rejected post '${suggestion.postTextTeaser()}' from ${suggestion.authorName} with comment '$rejectComment'")
                val editorLabel = "❌ Удалён $pressedBy в ${Instant.now().simpleFormatTime()}$commentPreview ❌"
                EditorSuggestionMenuHandler.renderFinishKeyboard(message, editorLabel.trimToLength(512, "…"))
                val userLabel = "❌ Отклонен$commentPreview ❌"
                UserSuggestionMenuHandler.renderFinishKeyboard(suggestion.authorChatId.toString(), suggestion.authorMessageId, userLabel.trimToLength(512, "…"))
            } else {
                EditorSuggestionMenuHandler.renderFinishKeyboard(message)
            }
        } else {
            EditorSuggestionMenuHandler.renderFinishKeyboard(message)
        }
    }

    override suspend fun createHandlerKeyboard(message: Message, pressedBy: String) = sequence {
            rejectComments
                .map { (key, comment) -> InlineKeyboardButton(
                    when (key) {
                        silentRejectPayload, customCommentPayload -> comment
                        else -> "❌ \uD83D\uDCAC \"$comment\" ❌"
                    }, callbackData(key)) }
                .forEach { yield(listOf(it)) }
            yield(listOf(EditorSuggestionMenuHandler.backButton))
        }.toList().let { InlineKeyboardMarkup(it) }

    override val command: String = "/reject"

    override suspend fun MessageContext.handle() {
        if (replyMessage == null) return
        when (val comment = messageText.removePrefix(command).trim()) {
            "" -> TelegramClient.sendChatMessage(chatId, TgTextOutput("Зачем использовать эту команду без комментария?"), message.id)
            else -> rejectCustomComment(replyMessage, message.from.simpleRef, comment)
        }
    }
}