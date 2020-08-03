package com.tgbt.telegram

import com.tgbt.telegram.output.TgImagePostOutput
import com.tgbt.telegram.output.TgMessageOutput
import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.url


class TgMessageSender(private val httpClient: HttpClient, apiToken: String) {

    private val apiUrl = "https://api.telegram.org/bot$apiToken"

    suspend fun editChatMessage(chatId: String, messageId: Long, output: TgMessageOutput) {
        httpClient.post<String> {
            url("$apiUrl/editMessageText")
            parameter("text", output.markdown())
            parameter("parse_mode", "Markdown")
            parameter("chat_id", chatId)
            parameter("message_id", messageId)
            val keyboardJson = output.keyboardJson()
            keyboardJson?.let { parameter("reply_markup", it) }
        }
    }

    suspend fun sendChatMessage(chatId: String, output: TgMessageOutput, replyMessageId: Long? = null) {
        httpClient.post<String> {
            url("$apiUrl/sendMessage")
            parameter("text", output.markdown())
            parameter("parse_mode", "Markdown")
            parameter("chat_id", chatId)
            replyMessageId?.let { parameter("reply_to_message_id", it.toString()) }
            output.keyboardJson()?.let { parameter("reply_markup", it) }
        }
    }

    suspend fun sendChatPhoto(chatId: String, output: TgImagePostOutput) {
        httpClient.post<String> {
            url("$apiUrl/sendPhoto")
            parameter("caption", output.markdown())
            parameter("photo", output.imageUrl())
            parameter("parse_mode", "Markdown")
            parameter("chat_id", chatId)
            output.keyboardJson()?.let { parameter("reply_markup", it) }
        }
    }

    suspend fun pingCallbackQuery(queryId: String) {
        httpClient.post<String> {
            url("$apiUrl/answerCallbackQuery")
            parameter("callback_query_id", queryId)
        }
    }

}