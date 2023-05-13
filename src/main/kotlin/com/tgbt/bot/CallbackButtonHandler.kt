package com.tgbt.bot

import com.tgbt.BotOwnerIds
import com.tgbt.bot.editor.button.*
import com.tgbt.misc.escapeMarkdown
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.api.CallbackQuery
import com.tgbt.telegram.api.Message
import com.tgbt.telegram.api.simpleRef
import com.tgbt.telegram.api.verboseLogReference
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
    abstract suspend fun renderNewMenu(message: Message, pressedBy: String): CallbackNotificationText

    protected fun throwInvalid(message: Message, data: String?): Nothing =
        throw IllegalStateException("Invalid payload '$data' supplied from ${message.verboseLogReference}")

    protected open suspend fun handle(message: Message, pressedBy: String, data: String?): CallbackNotificationText {
        logger.info("Inline '$data' by $pressedBy <- ${message.verboseLogReference}")
        val payload = data?.trimPrefix()
        return if (payload == null) {
            renderNewMenu(message, pressedBy)
        } else {
            if (isValidPayload(payload)) {
                handleButtonAction(message, pressedBy, payload)
            } else {
                throwInvalid(message, data)
            }
        }
    }
    companion object {
        private val AVAILABLE_BUTTONS: List<CallbackButtonHandler> = listOf(
            MainMenuHandler,
            ModifyImageMenuHandler,
            ModifyTextMenuHandler,
            RejectMenuHandler,
            BanMenuHandler,
            PostMenuHandler.PostPubliclyMenuHandler,
            PostMenuHandler.PostAnonymouslyMenuHandler,
            FinishedMenuHandler,
        )

        suspend fun handle(query: CallbackQuery): CallbackNotificationText {
            try {
                if (query.data == null || query.message == null) {
                    throw IllegalStateException("Callback Data and it's message is not supposed to be null, query dump: ${query}")
                }
                val handler = AVAILABLE_BUTTONS.find { it.canHandle(query) }
                    ?: throw IllegalStateException("Cannot find Handler for this query from ${AVAILABLE_BUTTONS.size} available; query dump: $query")
                return handler.handle(query.message, query.from.simpleRef, query.data)
            } catch (e: Exception) {
                val line = (e as? ClientRequestException)?.response?.bodyAsText()
                val message =
                    "Unexpected error occurred while handling callback query, error message:\n`${e.message?.escapeMarkdown()}`" +
                            (line?.let { "\n\nResponse content:\n```${line.escapeMarkdown()}```" } ?: "")
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