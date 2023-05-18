package com.tgbt.bot.editor.button

import com.tgbt.bot.button.SuggestionMenuHandler
import com.tgbt.bot.button.modify.ModifyImageMenuHandler
import com.tgbt.bot.button.modify.ModifyTextMenuHandler
import com.tgbt.suggestion.UserSuggestion
import com.tgbt.telegram.api.InlineKeyboardButton
import com.tgbt.telegram.api.InlineKeyboardMarkup


object EditorSuggestionMenuHandler: SuggestionMenuHandler("EDIT", searchByAuthor = false) {

    override val buttonToHandler = mapOf(
        "edit_images" to EditorModifyImageMenuHandler,
        "edit_text" to EditorModifyTextMenuHandler,
        "reject" to RejectMenuHandler,
        "ban" to BanMenuHandler,
        "anon" to PostMenuHandler.PostAnonymouslyMenuHandler,
        "deanon" to PostMenuHandler.PostPubliclyMenuHandler,
    )

    override suspend fun rootKeyboard(suggestion: UserSuggestion): InlineKeyboardMarkup {
        val imageEmoji = suggestion.imageId?.let { "✅" } ?: "⚠\uFE0F"
        val textEmoji = if (ModifyTextMenuHandler.isValidBugurt(suggestion.postText)) "✅" else "⚠\uFE0F"
        return InlineKeyboardMarkup(listOf(
            listOf(InlineKeyboardButton("\uD83D\uDDBC Картинка - $imageEmoji | Изменить", callbackData("edit_images"))),
            listOf(InlineKeyboardButton("\uD83D\uDCDD Текст - $textEmoji | Изменить", callbackData("edit_text"))),
            listOf(
                InlineKeyboardButton("❌ Отклонить", callbackData("reject")),
                InlineKeyboardButton("\uD83D\uDEAB Забанить", callbackData("ban"))
            ),
            listOf(
                InlineKeyboardButton("✅ Анонимно", callbackData("anon")),
                InlineKeyboardButton("☑️ Не анонимно", callbackData("deanon"))
            )
        ))
    }
}

object EditorModifyImageMenuHandler: ModifyImageMenuHandler("EDIT", searchByAuthor = false) {
    override fun retrieveMainMenuHandler() = EditorSuggestionMenuHandler
}

object EditorModifyTextMenuHandler: ModifyTextMenuHandler("EDIT", searchByAuthor = false) {
    override fun retrieveMainMenuHandler() = EditorSuggestionMenuHandler
}
