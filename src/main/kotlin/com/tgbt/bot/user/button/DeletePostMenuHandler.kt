package com.tgbt.bot.user.button

import com.tgbt.bot.button.CallbackButtonHandler
import com.tgbt.bot.button.CallbackNotificationText
import com.tgbt.suggestion.SuggestionStore
import com.tgbt.telegram.api.InlineKeyboardButton
import com.tgbt.telegram.api.InlineKeyboardMarkup
import com.tgbt.telegram.api.Message

object DeletePostMenuHandler: CallbackButtonHandler("USER", "DELETE") {

    private val deleteButton = InlineKeyboardButton("❌ Действительно удалить? ❌", callbackData("delete"))
    private val keyboard = InlineKeyboardMarkup(listOf(listOf(deleteButton), listOf(UserSuggestionMenuHandler.backButton)))

    override fun isValidPayload(payload: String): Boolean = payload == "delete"

    override suspend fun handleButtonAction(
        message: Message,
        pressedBy: String,
        validPayload: String
    ): CallbackNotificationText {
        val suggestion = SuggestionStore.findByMessage(message, byAuthor = true)
        return if (suggestion != null) {
            if (suggestion.editorMessageId == null) {
                SuggestionStore.removeByChatAndMessageId(suggestion.authorChatId, suggestion.authorMessageId, byAuthor = true)
                UserSuggestionMenuHandler.renderFinishKeyboard(message, "\uD83D\uDDD1 Пост удален без публикации \uD83D\uDDD1")
            } else {
                UserSuggestionMenuHandler.renderFinishKeyboard(message, "✅ Пост уже в редакции ✅")
            }
        } else {
            UserSuggestionMenuHandler.renderFinishKeyboard(message)
        }
    }

    override suspend fun createHandlerKeyboard(message: Message, pressedBy: String) = keyboard
}