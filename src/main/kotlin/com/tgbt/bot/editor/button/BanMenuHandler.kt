package com.tgbt.bot.editor.button

import com.tgbt.BotJson
import com.tgbt.ban.BanStore
import com.tgbt.ban.UserBan
import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.bot.button.CallbackButtonHandler
import com.tgbt.bot.button.CallbackNotificationText
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

object BanMenuHandler : CallbackButtonHandler("EDIT", "BAN"), BotCommand {

    private val logger = LoggerFactory.getLogger(this::class.simpleName)

    private val banComments = mapOf(
        "ddos" to "Заебал",
        "ddos2" to "Спамить нельзя",
        "over" to "Довыебывался",
        "fck" to "Ну и иди в бан",
        "calm" to "Посиди в бане, подумай над тем что скинул",
        "shame" to "Твоим родителям должно быть стыдно"
    )

    override fun isValidPayload(payload: String): Boolean = payload in banComments

    override suspend fun handleButtonAction(
        message: Message,
        pressedBy: String,
        validPayload: String,
    ): CallbackNotificationText {
        return banCustomComment(message, pressedBy, banComments.getValue(validPayload))
    }

    private suspend fun banCustomComment(message: Message, pressedBy: String, banComment: String): CallbackNotificationText {
        val suggestion = SuggestionStore.findByChatAndMessageId(message.chat.id, message.id, byAuthor = false)
        return if (suggestion?.editorChatId != null && suggestion.editorMessageId != null) {
            if (BanStore.findByChatId(suggestion.authorChatId) == null) {
                val ban = UserBan(
                    authorChatId = suggestion.authorChatId,
                    authorName = suggestion.authorName,
                    postTeaser = suggestion.postTextTeaser(),
                    reason = banComment,
                    bannedBy = pressedBy
                )
                BanStore.insert(ban)
                logger.info("User ${ban.authorName} was banned by ${ban.bannedBy}")
            }
            SuggestionStore.removeByChatAndMessageId(
                suggestion.editorChatId,
                suggestion.editorMessageId,
                byAuthor = false
            )
            TelegramClient.sendChatMessage(
                suggestion.authorChatId.toString(), TgTextOutput(
                    UserMessages.bannedErrorMessage
                        .format(suggestion.postTextTeaser().escapeMarkdown(), banComment.escapeMarkdown())
                )
            )
            logger.info("Editor ${message.from.simpleRef} banned a user ${suggestion.authorName} because of post '${suggestion.postTextTeaser()}', comment '$banComment'")
            val label =
                "\uD83D\uDEAB Забанен $pressedBy в ${Instant.now().simpleFormatTime()} \uD83D\uDCAC $banComment ❌"
            EditorMainMenuHandler.finishInteraction(message, label.trimToLength(512, "…"))
        } else {
            EditorMainMenuHandler.finishInteraction(message)
        }
    }

    override suspend fun renderNewMenu(message: Message, pressedBy: String): CallbackNotificationText {
        val keyboard = sequence {
            banComments
                .map { (key, comment) -> InlineKeyboardButton("❌ \uD83D\uDCAC \"$comment\" ❌", callbackData(key)) }
                .forEach { yield(listOf(it)) }
            yield(listOf(EditorMainMenuHandler.backButton))
        }.toList().let { InlineKeyboardMarkup(it) }
        val keyboardJson = BotJson.encodeToString(InlineKeyboardMarkup.serializer(), keyboard)
        TelegramClient.editChatMessageKeyboard(message.chat.id.toString(), message.id, keyboardJson)
        return null
    }


    override val command: String = "/ban "
    override suspend fun MessageContext.handle() {
        if (replyMessage == null) return
        when (val comment = messageText.removePrefix(command).trim()) {
            "" -> TelegramClient.sendChatMessage(chatId, TgTextOutput("Нужно указать причину блокировки"), message.id)
            else -> banCustomComment(replyMessage, message.from.simpleRef, comment)
        }
    }
}