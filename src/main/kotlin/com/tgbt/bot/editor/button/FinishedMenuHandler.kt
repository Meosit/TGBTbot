package com.tgbt.bot.editor.button

import com.tgbt.BotJson
import com.tgbt.bot.ButtonMenuHandler
import com.tgbt.bot.CallbackNotificationText
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.api.InlineKeyboardButton
import com.tgbt.telegram.api.InlineKeyboardMarkup
import com.tgbt.telegram.api.Message
import com.tgbt.telegram.api.toMarkup

class FinishedMenuHandler(
    private val label: String,
    private val optionalActions: List<InlineKeyboardButton>? = null
): ButtonMenuHandler("EDIT", "FINISH") {

    private val finishedPayload = callbackData("done")
    private fun createKeyboard(label: String) = InlineKeyboardButton(label, finishedPayload)

    override fun isValidPayload(payload: String): Boolean = payload == finishedPayload

    override suspend fun handleButtonAction(message: Message, pressedBy: String, validPayload: String): CallbackNotificationText {
        return null
    }

    override suspend fun renderNewMenu(message: Message, pressedBy: String): CallbackNotificationText {
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

}