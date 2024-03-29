package com.tgbt.bot.editor.button

import com.tgbt.ban.BanStore
import com.tgbt.ban.UserBan
import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.bot.button.CallbackButtonHandler
import com.tgbt.bot.button.CallbackNotificationText
import com.tgbt.bot.user.UserMessages
import com.tgbt.bot.user.button.UserSuggestionMenuHandler
import com.tgbt.misc.escapeMarkdown
import com.tgbt.misc.simpleFormatTime
import com.tgbt.misc.teaserString
import com.tgbt.misc.trimToLength
import com.tgbt.settings.Setting
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

    private const val customCommentPayload = "custom"
    private val banComments = mapOf(
        "ddos" to "Заебал",
        "ddos2" to "Спамить нельзя",
        "over" to "Довыебывался",
        "fck" to "Ну и иди в бан",
        "calm" to "Посиди в бане, подумай над тем что скинул",
        "shame" to "Твоим родителям должно быть стыдно",
        customCommentPayload to "ℹ\uFE0F Свой комментарий ℹ\uFE0F",
    )

    override fun isValidPayload(payload: String): Boolean = payload in banComments

    override suspend fun handleButtonAction(
        message: Message,
        pressedBy: String,
        validPayload: String,
    ): CallbackNotificationText = when (validPayload) {
        customCommentPayload -> "Используй команду: /ban <комментарий>"
        else -> banCustomComment(message, pressedBy, banComments.getValue(validPayload))
    }

    private suspend fun banCustomComment(message: Message, pressedBy: String, banComment: String): CallbackNotificationText {
        val suggestion = SuggestionStore.findByMessage(message, byAuthor = false)
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
                sendBanNotification(ban)
            }
            SuggestionStore.removeByChatAndMessageId(
                suggestion.editorChatId,
                suggestion.editorMessageId,
                byAuthor = false
            )
            logger.info("Editor $pressedBy banned a user ${suggestion.authorName} because of post '${suggestion.postTextTeaser()}', comment '$banComment'")
            val editorlabel = "\uD83D\uDEAB Забанен $pressedBy в ${Instant.now().simpleFormatTime()} \uD83D\uDCAC $banComment \uD83D\uDEAB"
            EditorSuggestionMenuHandler.renderFinishKeyboard(message, editorlabel.teaserString(150))
            val userLabel = "\uD83D\uDEAB Забанен \uD83D\uDCAC $banComment \uD83D\uDEAB"
            UserSuggestionMenuHandler.renderFinishKeyboard(suggestion.authorChatId.toString(), suggestion.authorMessageId, userLabel.trimToLength(512, "…"))
        } else {
            EditorSuggestionMenuHandler.renderFinishKeyboard(message)
        }
    }

    suspend fun sendBanNotification(ban: UserBan) {
        TelegramClient.sendChatMessage(ban.authorChatId.toString(), TgTextOutput(UserMessages.bannedErrorMessage.format(
            ban.postTeaser.escapeMarkdown(),
            ban.reason.escapeMarkdown(),
            Setting.UNBAN_REQUEST_COOL_DOWN_DAYS.str()
        )))
    }

    override suspend fun createHandlerKeyboard(message: Message, pressedBy: String) = sequence {
        banComments
            .map { (key, comment) -> InlineKeyboardButton(
                when (key) {
                    customCommentPayload -> comment
                    else -> "❌ \uD83D\uDCAC \"$comment\" ❌"
                }, callbackData(key)) }
            .forEach { yield(listOf(it)) }
        yield(listOf(EditorSuggestionMenuHandler.backButton))
    }.toList().let { InlineKeyboardMarkup(it) }


    override val command: String = "/ban"
    override suspend fun MessageContext.handle() {
        if (replyMessage == null) return
        when (val comment = messageText.removePrefix(command).trim()) {
            "" -> TelegramClient.sendChatMessage(chatId, TgTextOutput("Нужно указать причину блокировки"), message.id)
            else -> banCustomComment(replyMessage, message.from.simpleRef, comment)
        }
    }
}