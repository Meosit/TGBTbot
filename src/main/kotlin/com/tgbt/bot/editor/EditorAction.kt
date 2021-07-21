package com.tgbt.bot.editor

import com.tgbt.bot.BotContext
import com.tgbt.bot.user.UserMessages
import com.tgbt.misc.trimToLength
import com.tgbt.post.TgPreparedPost
import com.tgbt.sendTelegramPost
import com.tgbt.settings.Setting
import com.tgbt.suggestion.UserSuggestion
import com.tgbt.suggestion.authorReference
import com.tgbt.telegram.CallbackQuery
import com.tgbt.telegram.InlineKeyboardButton
import com.tgbt.telegram.InlineKeyboardMarkup
import com.tgbt.telegram.Message
import com.tgbt.telegram.output.TgTextOutput

object EditorAction {
    private const val DELETE_ACTION_DATA = "del"
    private const val CONFIRM_DELETE_ACTION_DATA = "del_confirm"
    private const val POST_ANONYMOUSLY_DATA = "anon"
    private const val CONFIRM_POST_ANONYMOUSLY_DATA = "anon_confirm"
    private const val POST_PUBLICLY_DATA = "deanon"
    private const val CONFIRM_POST_PUBLICLY_DATA = "deanon_confirm"

    private const val CANCEL_DATA = "cancel"
    private const val DELETED_DATA = "deleted"

    val ACTION_KEYBOARD = InlineKeyboardMarkup(listOf(
        listOf(InlineKeyboardButton("❌ Удалить пост", DELETE_ACTION_DATA)),
        listOf(
            InlineKeyboardButton("✅ Анонимно", POST_ANONYMOUSLY_DATA),
            InlineKeyboardButton("☑️ Не анонимно", POST_PUBLICLY_DATA)
        )
    ))

    suspend fun handleActionCallback(bot: BotContext, callback: CallbackQuery) {
        if (callback.data == DELETED_DATA) {
            bot.tgMessageSender.pingCallbackQuery(callback.id)
            return
        }
        val message: Message? = callback.message
        if (message == null) {
            bot.tgMessageSender.pingCallbackQuery(callback.id,
                "Сообщение устарело и недоступно боту, надо постить вручную")
            return
        }
        val suggestion = if (callback.data?.endsWith("_confirm") == true) {
            bot.suggestionStore.findByChatAndMessageId(message.chat.id, message.id, byAuthor = false)
        } else {
            null
        }
        when(callback.data) {
            DELETE_ACTION_DATA -> bot.sendConfirmDialog(message, callback,
                InlineKeyboardButton("❌ Удалить", CONFIRM_DELETE_ACTION_DATA))
            POST_ANONYMOUSLY_DATA -> bot.sendConfirmDialog(message, callback,
                InlineKeyboardButton("✅ Анонимно", CONFIRM_POST_ANONYMOUSLY_DATA))
            POST_PUBLICLY_DATA -> bot.sendConfirmDialog(message, callback,
                InlineKeyboardButton("☑️ Не анонимно", CONFIRM_POST_PUBLICLY_DATA))
            CONFIRM_DELETE_ACTION_DATA -> {
                if (suggestion?.editorChatId != null && suggestion.editorMessageId != null) {
                    bot.suggestionStore.removeByChatAndMessageId(suggestion.editorChatId, suggestion.editorMessageId, byAuthor = false)
                    if (bot.settings[Setting.SEND_DELETION_FEEDBACK].toBoolean()) {
                        bot.tgMessageSender.sendChatMessage(suggestion.authorChatId.toString(),
                            TgTextOutput(UserMessages.postDiscardedMessage.format(suggestion.postText.trimToLength(20, "..."))))
                    }
                }
                bot.sendDeletedConfirmation(message, callback, "❌ Пост был удалён ❌")
            }
            CONFIRM_POST_PUBLICLY_DATA -> {
                bot.sendSuggestion(suggestion, message, callback, anonymous = false)
            }
            CONFIRM_POST_ANONYMOUSLY_DATA -> {
                if (suggestion?.editorChatId != null && suggestion.editorMessageId != null) {
                    val channel = bot.settings[Setting.TARGET_CHANNEL]
                    val footerMd = bot.settings[Setting.FOOTER_MD]
                    val post = TgPreparedPost(
                        suggestion.postText, suggestion.imageId, footerMarkdown = footerMd,
                        suggestionReference = suggestion.authorReference(true)
                    )
                    bot.sendTelegramPost(channel, post)
                    bot.suggestionStore.removeByChatAndMessageId(suggestion.editorChatId, suggestion.editorMessageId, byAuthor = false)
                    bot.sendDeletedConfirmation(message, callback, "✅ Пост был опубликован ✅️")
                } else {
                    bot.sendDeletedConfirmation(message, callback, "❌ Пост не найден ❌")
                }
            }
            CANCEL_DATA -> {
                val keyboardJson = bot.json.stringify(InlineKeyboardMarkup.serializer(), ACTION_KEYBOARD)
                bot.tgMessageSender.editChatKeyboard(message.chat.id.toString(), message.id, keyboardJson)
                bot.tgMessageSender.pingCallbackQuery(callback.id, "Действие отменено")
            }
            else -> {
                bot.tgMessageSender.pingCallbackQuery(callback.id,
                    "Нераспознанные данные '${callback.data}'")
            }
        }
    }

    private suspend fun BotContext.sendSuggestion(
        suggestion: UserSuggestion?,
        message: Message,
        callback: CallbackQuery,
        anonymous: Boolean
    ) {
        if (suggestion?.editorChatId != null && suggestion.editorMessageId != null) {
            val channel = settings[Setting.TARGET_CHANNEL]
            val footerMd = settings[Setting.FOOTER_MD]
            val post = TgPreparedPost(
                suggestion.postText, suggestion.imageId, footerMarkdown = footerMd,
                suggestionReference = suggestion.authorReference(anonymous)
            )
            sendTelegramPost(channel, post)
            suggestionStore.removeByChatAndMessageId(suggestion.editorChatId, suggestion.editorMessageId, byAuthor = false)
            val emoji = if (anonymous) "✅" else "☑️"
            sendDeletedConfirmation(message, callback, "$emoji Пост был опубликован $emoji")
            if (settings[Setting.SEND_PROMOTION_FEEDBACK].toBoolean()) {
                tgMessageSender.sendChatMessage(suggestion.authorChatId.toString(),
                    TgTextOutput(UserMessages.postPromotedMessage.format(suggestion.postText.trimToLength(20, "..."))))
            }
        } else {
            sendDeletedConfirmation(message, callback, "❌ Пост не найден ❌")
        }
    }

    private suspend fun BotContext.sendConfirmDialog(
        message: Message,
        callback: CallbackQuery,
        actionButton: InlineKeyboardButton
    ) {
        val inlineKeyboardMarkup = InlineKeyboardMarkup(
            listOf(listOf(actionButton, InlineKeyboardButton("↩️ Вернуться", CANCEL_DATA)))
        )
        val keyboardJson = json.stringify(InlineKeyboardMarkup.serializer(), inlineKeyboardMarkup)
        tgMessageSender.editChatKeyboard(message.chat.id.toString(), message.id, keyboardJson)
        tgMessageSender.pingCallbackQuery(callback.id)
    }

    private suspend fun BotContext.sendDeletedConfirmation(
        message: Message,
        callback: CallbackQuery,
        buttonLabel: String
    ) {
        val inlineKeyboardMarkup = InlineKeyboardMarkup(listOf(listOf(InlineKeyboardButton(buttonLabel, DELETED_DATA))))
        val keyboardJson = json.stringify(InlineKeyboardMarkup.serializer(), inlineKeyboardMarkup)
        tgMessageSender.editChatKeyboard(message.chat.id.toString(), message.id, keyboardJson)
        tgMessageSender.pingCallbackQuery(callback.id, buttonLabel)
    }
}