package com.tgbt.bot.editor.button

import com.tgbt.BotJson
import com.tgbt.bot.CallbackButtonHandler
import com.tgbt.bot.CallbackNotificationText
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.api.InlineKeyboardButton
import com.tgbt.telegram.api.InlineKeyboardMarkup
import com.tgbt.telegram.api.Message

object MainMenuHandler: CallbackButtonHandler("EDIT", "MAIN") {

    val ROOT_KEYBOARD = InlineKeyboardMarkup(listOf(
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

    val BACK_TO_MAIN_BUTTON = InlineKeyboardButton("↩️ Назад", callbackData("back"))

    private val buttonToHandler = mapOf(
        "edit_images" to ModifyImageMenuHandler,
        "edit_text" to ModifyTextMenuHandler,
        "reject" to RejectMenuHandler,
        "ban" to BanMenuHandler,
        "anon" to PostMenuHandler.PostAnonymouslyMenuHandler,
        "deanon" to PostMenuHandler.PostPubliclyMenuHandler,
        "back" to MainMenuHandler
    )
    override fun isValidPayload(payload: String): Boolean = payload in buttonToHandler

    override suspend fun handleButtonAction(message: Message, pressedBy: String, validPayload: String): CallbackNotificationText {
        val menuHandler = buttonToHandler[validPayload]
            ?: throw IllegalStateException("Cannot find ${this::class.simpleName} for payload $validPayload")
        return menuHandler.renderNewMenu(message, pressedBy)
    }

    override suspend fun renderNewMenu(message: Message, pressedBy: String): CallbackNotificationText {
        val keyboardJson = BotJson.encodeToString(InlineKeyboardMarkup.serializer(), ROOT_KEYBOARD)
        TelegramClient.editChatMessageKeyboard(message.chat.id.toString(), message.id, keyboardJson)
        return null
    }
}