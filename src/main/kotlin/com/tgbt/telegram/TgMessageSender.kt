package com.tgbt.telegram

import com.tgbt.telegram.output.TgImageOutput
import com.tgbt.telegram.output.TgMessageOutput
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*


class TgMessageSender(private val httpClient: HttpClient, apiToken: String) {

    private val apiUrl = "https://api.telegram.org/bot$apiToken"

    suspend fun editChatMessageKeyboard(chatId: String, messageId: Long, keyboardJson: String) =
        httpClient.post {
            url("$apiUrl/editMessageReplyMarkup")
            parameter("chat_id", chatId)
            parameter("message_id", messageId)
            parameter("reply_markup", keyboardJson)
        }.body<TelegramMessageResponse>()

    suspend fun editChatMessageText(chatId: String, messageId: Long, message: String, keyboardJson: String?, disableLinkPreview: Boolean = false) =
        httpClient.post {
            url("$apiUrl/editMessageText")
            parameter("chat_id", chatId)
            parameter("message_id", messageId)
            parameter("text", message)
            parameter("parse_mode", "Markdown")
            parameter("disable_web_page_preview", disableLinkPreview)
            keyboardJson?.let { parameter("reply_markup", it) }
        }.body<TelegramMessageResponse>()

    suspend fun editChatMessageCaption(chatId: String, messageId: Long, caption: String, keyboardJson: String?) =
        httpClient.post {
            url("$apiUrl/editMessageCaption")
            parameter("chat_id", chatId)
            parameter("message_id", messageId)
            parameter("caption", caption)
            parameter("parse_mode", "Markdown")
            keyboardJson?.let { parameter("reply_markup", it) }
        }.body<TelegramMessageResponse>()

    suspend fun editChatMessagePhoto(chatId: String, messageId: Long, imageUrl: String) =
        httpClient.post {
            url("$apiUrl/editMessageMedia")
            parameter("chat_id", chatId)
            parameter("message_id", messageId)
            parameter("media", """{"type":"photo","media":"$imageUrl"}""")
        }.body<TelegramMessageResponse>()

    suspend fun sendChatMessage(chatId: String, output: TgMessageOutput, replyMessageId: Long? = null, disableLinkPreview: Boolean = false) =
        httpClient.post {
            url("$apiUrl/sendMessage")
            parameter("text", output.markdown())
            parameter("parse_mode", "Markdown")
            parameter("chat_id", chatId)
            parameter("disable_web_page_preview", disableLinkPreview)
            replyMessageId?.let { parameter("reply_to_message_id", it.toString()) }
            output.keyboardJson()?.let { parameter("reply_markup", it) }
        }.body<TelegramMessageResponse>()

    suspend fun sendChatPhoto(chatId: String, output: TgImageOutput) =
        httpClient.post {
            url("$apiUrl/sendPhoto")
            parameter("caption", output.markdown())
            parameter("photo", output.imageUrl())
            parameter("parse_mode", "Markdown")
            parameter("chat_id", chatId)
            output.keyboardJson()?.let { parameter("reply_markup", it) }
        }.body<TelegramMessageResponse>()

    suspend fun pingCallbackQuery(queryId: String, notificationText: String? = null) {
        httpClient.post {
            url("$apiUrl/answerCallbackQuery")
            parameter("callback_query_id", queryId)
            notificationText?.let { parameter("text", it) }
        }
    }

    suspend fun leaveGroup(chatId: String) {
        httpClient.post {
            url("$apiUrl/leaveChat")
            parameter("chat_id", chatId)
        }
    }

}