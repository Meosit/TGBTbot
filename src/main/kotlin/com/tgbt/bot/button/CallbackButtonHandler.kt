package com.tgbt.bot.button

import com.tgbt.BotOwnerIds
import com.tgbt.bot.editor.button.EditorSuggestionMenuHandler
import com.tgbt.bot.user.button.UserSuggestionMenuHandler
import com.tgbt.misc.escapeMarkdown
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.api.*
import com.tgbt.telegram.output.TgTextOutput
import io.ktor.client.plugins.*
import io.ktor.client.statement.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(CallbackButtonHandler::class.simpleName)


typealias CallbackNotificationText = String?

abstract class CallbackButtonHandler(category: String, id: String) {

    private val dataPrefix = "${category}|$id|"

    open fun canHandle(query: CallbackQuery) = query.data?.startsWith(dataPrefix) == true

    protected fun callbackData(data: String) = "$dataPrefix$data"

    protected fun String.trimPrefix() = removePrefix(dataPrefix)


    protected abstract fun isValidPayload(payload: String): Boolean

    protected abstract suspend fun handleButtonAction(message: Message, pressedBy: String, validPayload: String): CallbackNotificationText

    abstract suspend fun createHandlerKeyboard(message: Message, pressedBy: String): InlineKeyboardMarkup

    protected open suspend fun handle(message: Message, pressedBy: String, data: String?): CallbackNotificationText {
        logger.info("Inline '$data' by $pressedBy <- ${message.verboseLogReference}")
        val payload = data?.trimPrefix()
        return if (payload != null && isValidPayload(payload)) {
                handleButtonAction(message, pressedBy, payload)
            } else {
                throw IllegalStateException("Invalid payload '$data' supplied from ${message.verboseLogReference}")
            }
    }
    companion object {
        private val AVAILABLE_BUTTONS: List<CallbackButtonHandler> by lazy {
            EditorSuggestionMenuHandler.allHandlers + UserSuggestionMenuHandler.allHandlers
        }

        suspend fun handle(query: CallbackQuery): CallbackNotificationText {
            try {
                if (query.data == null || query.message == null) {
                    throw IllegalStateException("Callback Data and it's message is not supposed to be null, query dump: $query")
                }
                logger.info("Looking up callback from ${AVAILABLE_BUTTONS.size} buttons")
                val handler = AVAILABLE_BUTTONS.find { it.canHandle(query) }
                    ?: throw IllegalStateException("Cannot find Handler for this query from ${AVAILABLE_BUTTONS.size} available; " +
                            "query: ${query.data}, user ${query.from.id} ${query.from.simpleRef}; post ${query.message.chat.id}:${query.message.id}")
                return handler.handle(query.message, query.from.simpleRef, query.data)
            } catch (e: Exception) {
                val line = (e as? ClientRequestException)?.response?.bodyAsText()
                val message =
                    "Unexpected error occurred while handling callback query, error message:\n`${e.message?.escapeMarkdown()}`" +
                            (line?.let { "\n\nResponse content:\n```${line.escapeMarkdown()}```" } ?: "") +
                            "query: ${query.data}, user ${query.from.id} ${query.from.simpleRef}; post ${query.message?.chat?.id}:${query.message?.id}"
                logger.error(message, e)
                if (line != null) {
                    logger.error(line)
                }
                val output = TgTextOutput(message)
                BotOwnerIds.forEach { TelegramClient.sendChatMessage(it, output) }
                return "Произошла ошибка, ждите"
            }
        }
    }

}