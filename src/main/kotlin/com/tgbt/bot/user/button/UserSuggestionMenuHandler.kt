package com.tgbt.bot.user.button

import com.tgbt.bot.button.SuggestionMenuHandler
import com.tgbt.bot.button.modify.ModifyImageMenuHandler
import com.tgbt.bot.button.modify.ModifyTextMenuHandler
import com.tgbt.suggestion.UserSuggestion
import com.tgbt.suggestion.userEditSecondsRemaining
import com.tgbt.suggestion.userNewPostSecondsRemaining
import com.tgbt.telegram.api.InlineKeyboardButton
import com.tgbt.telegram.api.InlineKeyboardMarkup

object UserSuggestionMenuHandler: SuggestionMenuHandler("USER", searchByAuthor = true) {

    override val buttonToHandler = mapOf(
        "edit_images" to UserModifyImageMenuHandler,
        "edit_text" to UserModifyTextMenuHandler,
        "delete" to DeletePostMenuHandler,
        "send_now" to SendNowPostMenuHandler,
    )

    override suspend fun rootKeyboard(suggestion: UserSuggestion): InlineKeyboardMarkup {
        val imageEmoji = suggestion.imageId?.let { "✅" } ?: "⚠\uFE0F"
        val textEmoji = if (ModifyTextMenuHandler.isValidBugurt(suggestion.postText)) "✅" else "⚠\uFE0F"
        val editSecondsRemaining = suggestion.userEditSecondsRemaining()
        val editEndTime = with(editSecondsRemaining) { "${this / 60}:${(this % 60).toString().padStart(2, '0')}" }
        val newPostSecondsRemaining = suggestion.userNewPostSecondsRemaining()
        val nextPostTime = with(newPostSecondsRemaining) { "${this / 60}:${(this % 60).toString().padStart(2, '0')}" }
        val title = if (editSecondsRemaining > 0) "⬆\uFE0F Превью | ⏲ $editEndTime ⬆\uFE0F" else "⏳ Ожидает отправки в редакцию ⏳"
        return InlineKeyboardMarkup(listOfNotNull(
            listOf(InlineKeyboardButton(title, backButton.callbackData)),
            listOf(InlineKeyboardButton("\uD83D\uDD02 Через $nextPostTime - новый пост \uD83D\uDD02", backButton.callbackData))
                .takeIf { newPostSecondsRemaining > 0 },
            listOf(InlineKeyboardButton("\uD83D\uDDBC Картинка - $imageEmoji | Изменить", callbackData("edit_images")))
                .takeIf { editSecondsRemaining > 0 },
            listOf(InlineKeyboardButton("\uD83D\uDCDD Текст - $textEmoji | Изменить", callbackData("edit_text")))
                .takeIf { editSecondsRemaining > 0 },
            listOf(InlineKeyboardButton("❌ Удалить пост ❌", callbackData("delete")))
                .takeIf { suggestion.editorMessageId == null },
            listOf(InlineKeyboardButton("✅ Отправить сейчас ✅", callbackData("send_now")))
                .takeIf { suggestion.editorMessageId == null }
        ))
    }

}

object UserModifyImageMenuHandler: ModifyImageMenuHandler("USER", searchByAuthor = true) {
    override fun retrieveMainMenuHandler() = UserSuggestionMenuHandler
}

object UserModifyTextMenuHandler: ModifyTextMenuHandler("USER", searchByAuthor = true) {
    override fun retrieveMainMenuHandler() = UserSuggestionMenuHandler
}
