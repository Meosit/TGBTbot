package com.tgbt.telegram

import com.tgbt.BotHttpClient
import com.tgbt.BotToken
import com.tgbt.telegram.api.ApiResponse
import com.tgbt.telegram.api.BooleanApiResponse
import com.tgbt.telegram.api.MessageApiResponse
import com.tgbt.telegram.output.TgImageOutput
import com.tgbt.telegram.output.TgMessageOutput
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory


object TelegramClient {

    private val logger = LoggerFactory.getLogger(TelegramClient::class.simpleName)
    private val apiUrl = "https://api.telegram.org/bot$BotToken"

    private suspend inline fun <reified T: ApiResponse>postWithBackpressure(maxTries: Int = 5, block: HttpRequestBuilder.() -> Unit): T {
        var tries = 0
        while (tries < maxTries) {
            try {
                return BotHttpClient.post(block).body()
            } catch (e: ClientRequestException) {
                val response = e.response.body<T>()
                val parameters = response.parameters
                when {
                    response.errorCode == 429 && parameters?.retryAfter != null -> {
                        val retryAfter = parameters.retryAfter
                        logger.warn("Got '${response.description}'. Retrying request in $retryAfter seconds")
                        delay(retryAfter * 1000 + 500)
                        tries++
                    }
                    response.errorCode == 403 -> {
                        logger.warn("Got '${response.description}'. Ignoring it")
                        return response
                    }
                    else -> throw e
                }
            }
        }
        throw IllegalStateException("Telegram request reached limit of $maxTries tries")
    }

    suspend fun editChatMessageKeyboard(chatId: String, messageId: Long, keyboardJson: String
    ) = postWithBackpressure<MessageApiResponse> {
        url("$apiUrl/editMessageReplyMarkup")
        parameter("chat_id", chatId)
        parameter("message_id", messageId)
        parameter("reply_markup", keyboardJson)
    }

    suspend fun editChatMessageText(
        chatId: String, messageId: Long, message: String, keyboardJson: String?, disableLinkPreview: Boolean = false
    ) = postWithBackpressure<MessageApiResponse> {
        url("$apiUrl/editMessageText")
        parameter("chat_id", chatId)
        parameter("message_id", messageId)
        parameter("text", message)
        parameter("parse_mode", "Markdown")
        parameter("disable_web_page_preview", disableLinkPreview)
        keyboardJson?.let { parameter("reply_markup", it) }
    }

    suspend fun editChatMessageCaption(chatId: String, messageId: Long, caption: String, keyboardJson: String?) =
        postWithBackpressure<MessageApiResponse> {
            url("$apiUrl/editMessageCaption")
            parameter("chat_id", chatId)
            parameter("message_id", messageId)
            parameter("caption", caption)
            parameter("parse_mode", "Markdown")
            keyboardJson?.let { parameter("reply_markup", it) }
        }

    suspend fun editChatMessagePhoto(chatId: String, messageId: Long, imageUrl: String
    ) = postWithBackpressure<MessageApiResponse> {
        url("$apiUrl/editMessageMedia")
        parameter("chat_id", chatId)
        parameter("message_id", messageId)
        parameter("media", """{"type":"photo","media":"$imageUrl"}""")
    }

    suspend fun sendChatMessage(
        chatId: String, output: TgMessageOutput, replyMessageId: Long? = null, disableLinkPreview: Boolean = false
    ) = postWithBackpressure<MessageApiResponse> {
        url("$apiUrl/sendMessage")
        parameter("text", output.markdown())
        parameter("parse_mode", "Markdown")
        parameter("chat_id", chatId)
        parameter("disable_web_page_preview", disableLinkPreview)
        replyMessageId?.let { parameter("reply_to_message_id", it.toString()) }
        output.keyboardJson()?.let { parameter("reply_markup", it) }
    }

    suspend fun sendChatPhoto(chatId: String, output: TgImageOutput) = postWithBackpressure<MessageApiResponse> {
        url("$apiUrl/sendPhoto")
        parameter("caption", output.markdown())
        parameter("photo", output.imageUrl())
        parameter("parse_mode", "Markdown")
        parameter("chat_id", chatId)
        output.keyboardJson()?.let { parameter("reply_markup", it) }
    }

    suspend fun pingCallbackQuery(queryId: String, notificationText: String? = null) {
        postWithBackpressure<BooleanApiResponse> {
            url("$apiUrl/answerCallbackQuery")
            parameter("callback_query_id", queryId)
            notificationText?.let { parameter("text", it) }
        }
    }

    suspend fun leaveGroup(chatId: String) {
        postWithBackpressure<BooleanApiResponse> {
            url("$apiUrl/leaveChat")
            parameter("chat_id", chatId)
        }
    }

}