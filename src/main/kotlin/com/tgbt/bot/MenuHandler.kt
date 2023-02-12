package com.tgbt.bot

import com.tgbt.telegram.api.CallbackQuery
import com.tgbt.telegram.api.Message
import com.tgbt.telegram.api.verboseLogReference
import java.util.logging.Logger

private val logger = Logger.getLogger(MenuHandler::class.simpleName)


typealias CallbackNotificationText = String?

abstract class MenuHandler(category: String, id: String) {

    private val dataPrefix = "${category}|$id|"

    init {
        if (dataPrefix.encodeToByteArray().size > 32) {
            throw IllegalStateException("Data prefix '$dataPrefix' of ${this::class.simpleName} is longer than 32 bytes")
        }
    }

    open fun canHandle(query: CallbackQuery) = query.data?.startsWith(dataPrefix) == true

    protected fun callbackData(data: String) = "$dataPrefix$data"

    protected fun String.trimPrefix() = removePrefix(dataPrefix)

    protected abstract fun isValidPayload(payload: String): Boolean

    protected abstract suspend fun handleButtonAction(message: Message, pressedBy: String, validPayload: String): CallbackNotificationText
    protected abstract suspend fun renderNewMenu(message: Message, pressedBy: String): CallbackNotificationText

    protected fun throwInvalid(message: Message, data: String?): Nothing =
        throw IllegalStateException("Invalid payload '$data' supplied from ${message.verboseLogReference}")

    open suspend fun handle(message: Message, pressedBy: String, data: String?): CallbackNotificationText {
        logger.info("$data by $pressedBy <- ${message.verboseLogReference}")
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

}