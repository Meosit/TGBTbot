package com.tgbt.bot.editor.button

import com.tgbt.bot.button.MainMenuHandler
import com.tgbt.bot.button.modify.ModifyImageMenuHandler
import com.tgbt.bot.button.modify.ModifyTextMenuHandler
import com.tgbt.telegram.api.InlineKeyboardButton
import com.tgbt.telegram.api.InlineKeyboardMarkup



object EditorMainMenuHandler: MainMenuHandler("EDIT") {

    override val buttonToHandler = mapOf(
        "edit_images" to EditorModifyImageMenuHandler,
        "edit_text" to EditorModifyTextMenuHandler,
        "reject" to RejectMenuHandler,
        "ban" to BanMenuHandler,
        "anon" to PostMenuHandler.PostAnonymouslyMenuHandler,
        "deanon" to PostMenuHandler.PostPubliclyMenuHandler,
    )

    override val rootKeyboard = InlineKeyboardMarkup(listOf(
        listOf(
            InlineKeyboardButton("\uD83D\uDDBC Картинки", callbackData("edit_images")),
            InlineKeyboardButton("\uD83D\uDCDD Текст", callbackData("edit_text")),
        ),
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

object EditorModifyImageMenuHandler: ModifyImageMenuHandler("EDIT", searchByAuthor = false) {
    override fun retrieveMainMenuHandler() = EditorMainMenuHandler
}

object EditorModifyTextMenuHandler: ModifyTextMenuHandler("EDIT", searchByAuthor = false) {
    override fun retrieveMainMenuHandler() = EditorMainMenuHandler
}
