package com.tgbt.bot.user.button

import com.tgbt.bot.button.CallbackButtonHandler
import com.tgbt.bot.button.CallbackNotificationText
import com.tgbt.bot.owner.ForcePublishSuggestionsCommand
import com.tgbt.suggestion.SuggestionStore
import com.tgbt.telegram.api.InlineKeyboardButton
import com.tgbt.telegram.api.InlineKeyboardMarkup
import com.tgbt.telegram.api.Message

object SendNowPostMenuHandler: CallbackButtonHandler("USER", "SEND") {

    private val sendButton = InlineKeyboardButton("✅ Действительно отправить? ✅", callbackData("send"))
    private val keyboard = InlineKeyboardMarkup(listOf(listOf(sendButton), listOf(UserSuggestionMenuHandler.backButton)))

    override fun isValidPayload(payload: String): Boolean = payload == "send"

    override suspend fun handleButtonAction(
        message: Message,
        pressedBy: String,
        validPayload: String
    ): CallbackNotificationText {
        val suggestion = SuggestionStore.findByMessage(message, byAuthor = true)
        if (suggestion != null && suggestion.editorMessageId == null) {
            ForcePublishSuggestionsCommand.sendPostForReview(suggestion)
        }
        return UserSuggestionMenuHandler.renderFinishKeyboard(message, "✅ Пост уже в редакции ✅")
    }

    override suspend fun createHandlerKeyboard(message: Message, pressedBy: String) = keyboard
}