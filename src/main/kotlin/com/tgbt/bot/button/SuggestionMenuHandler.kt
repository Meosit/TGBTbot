package com.tgbt.bot.button
import com.tgbt.BotJson
import com.tgbt.suggestion.SuggestionStore
import com.tgbt.suggestion.UserSuggestion
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.api.InlineKeyboardButton
import com.tgbt.telegram.api.InlineKeyboardMarkup
import com.tgbt.telegram.api.Message
import com.tgbt.telegram.api.toMarkup

abstract class SuggestionMenuHandler(category: String, val searchByAuthor: Boolean): CallbackButtonHandler(category, "MAIN") {

    protected abstract val buttonToHandler: Map<String, CallbackButtonHandler>
    abstract suspend fun rootKeyboard(suggestion: UserSuggestion): InlineKeyboardMarkup

    private val finishPayload = "done"
    private val backPayload = "back"
    val backButton = InlineKeyboardButton("↩️ Назад", callbackData(backPayload))

    override fun isValidPayload(payload: String): Boolean =
        payload == backPayload || payload == finishPayload || payload in buttonToHandler

    override suspend fun handleButtonAction(message: Message, pressedBy: String, validPayload: String): CallbackNotificationText {
        val keyboard = when (validPayload) {
            backPayload -> createHandlerKeyboard(message, pressedBy)
            finishPayload -> message.replyMarkup ?: createFinishKeyboard()
            else -> {
                val menuHandler = buttonToHandler[validPayload]
                    ?: throw IllegalStateException("Cannot find ${this::class.simpleName} for payload $validPayload")
                menuHandler.createHandlerKeyboard(message, pressedBy)
            }
        }
        println("Root keyboard generated")
        val keyboardJson = BotJson.encodeToString(InlineKeyboardMarkup.serializer(), keyboard)
        println("Keyboard serialized")
        TelegramClient.editChatMessageKeyboard(message.chat.id.toString(), message.id, keyboardJson)
        return null
    }

    override suspend fun createHandlerKeyboard(message: Message, pressedBy: String): InlineKeyboardMarkup {
        val suggestion = SuggestionStore.findByMessage(message, searchByAuthor)
        return if (suggestion == null) {
            createFinishKeyboard()
        } else {
            rootKeyboard(suggestion)
        }
    }

    protected open val notFoundFinishLabel = "\uD83D\uDD12 Пост уже недоступен \uD83D\uDD12"

    private fun createFinishKeyboard(
        label: String = notFoundFinishLabel,
        optionalActions: List<InlineKeyboardButton>? = null
    ): InlineKeyboardMarkup {
        val finishButton = InlineKeyboardButton(label, callbackData(finishPayload))
        return if (optionalActions == null) {
            finishButton.toMarkup()
        } else {
            InlineKeyboardMarkup(listOf(listOf(finishButton), optionalActions))
        }
    }

    suspend fun renderFinishKeyboard(
        chatId: String, messageId: Long,
        label: String = notFoundFinishLabel,
        optionalActions: List<InlineKeyboardButton>? = null
    ): CallbackNotificationText {
        val keyboardJson = BotJson.encodeToString(InlineKeyboardMarkup.serializer(), createFinishKeyboard(label, optionalActions))
        TelegramClient.editChatMessageKeyboard(chatId, messageId, keyboardJson)
        return label
    }

    suspend fun renderFinishKeyboard(
        message: Message,
        label: String = notFoundFinishLabel,
        optionalActions: List<InlineKeyboardButton>? = null
    ) = renderFinishKeyboard(message.chat.id.toString(), message.id, label, optionalActions)

    val allHandlers by lazy { listOf(this, *buttonToHandler.values.toTypedArray()) }
}