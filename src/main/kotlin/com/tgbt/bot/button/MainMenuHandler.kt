package com.tgbt.bot.button

import com.tgbt.BotJson
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.api.InlineKeyboardButton
import com.tgbt.telegram.api.InlineKeyboardMarkup
import com.tgbt.telegram.api.Message

abstract class MainMenuHandler(category: String): CallbackButtonHandler(category, "MAIN") {
    private val finishHandler = FinishedMenuHandler(category)

    protected abstract val buttonToHandler: Map<String, CallbackButtonHandler>
    abstract val rootKeyboard: InlineKeyboardMarkup
    private val backPayload = "back"
    val backButton = InlineKeyboardButton("↩️ Назад", callbackData(backPayload))

    override fun isValidPayload(payload: String): Boolean = payload == backPayload || payload in buttonToHandler

    override suspend fun handleButtonAction(message: Message, pressedBy: String, validPayload: String): CallbackNotificationText {
        if (validPayload == backPayload) {
            return renderNewMenu(message, pressedBy)
        }
        val menuHandler = buttonToHandler[validPayload]
            ?: throw IllegalStateException("Cannot find ${this::class.simpleName} for payload $validPayload")
        return menuHandler.renderNewMenu(message, pressedBy)
    }

    override suspend fun renderNewMenu(message: Message, pressedBy: String): CallbackNotificationText {
        val keyboardJson = BotJson.encodeToString(InlineKeyboardMarkup.serializer(), rootKeyboard)
        TelegramClient.editChatMessageKeyboard(message.chat.id.toString(), message.id, keyboardJson)
        return null
    }

    protected open val notFoundFinishLabel = "❔ Пост не найден ❔"

    suspend fun finishInteraction(message: Message, label: String = notFoundFinishLabel, optionalActions: List<InlineKeyboardButton>? = null) =
        finishHandler.finish(message, label, optionalActions)

    val allHandlers by lazy { listOf(this, *buttonToHandler.values.toTypedArray(), finishHandler) }
}