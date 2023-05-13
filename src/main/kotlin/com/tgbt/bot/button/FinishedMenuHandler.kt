package com.tgbt.bot.button

import com.tgbt.BotJson
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.api.InlineKeyboardButton
import com.tgbt.telegram.api.InlineKeyboardMarkup
import com.tgbt.telegram.api.Message
import com.tgbt.telegram.api.toMarkup

class FinishedMenuHandler(category: String): CallbackButtonHandler(category, "FINISH") {

    private val finishedPayload = callbackData("done")
    private fun createKeyboard(label: String) = InlineKeyboardButton(label, finishedPayload)

    override fun isValidPayload(payload: String): Boolean = payload == finishedPayload

    override suspend fun handleButtonAction(message: Message, pressedBy: String, validPayload: String): CallbackNotificationText {
        return null
    }

    suspend fun finish(message: Message, label: String = "❔ Пост не найден ❔", optionalActions: List<InlineKeyboardButton>? = null): CallbackNotificationText {
        val finishedButton = createKeyboard(label)
        val inlineKeyboardMarkup = if (optionalActions == null) {
            finishedButton.toMarkup()
        } else {
            InlineKeyboardMarkup(listOf(listOf(finishedButton), optionalActions))
        }
        val keyboardJson = BotJson.encodeToString(InlineKeyboardMarkup.serializer(), inlineKeyboardMarkup)
        TelegramClient.editChatMessageKeyboard(message.chat.id.toString(), message.id, keyboardJson)
        return label
    }

    override suspend fun renderNewMenu(message: Message, pressedBy: String): CallbackNotificationText {
        throw IllegalStateException("Finished action cannot render new menu")
    }

}