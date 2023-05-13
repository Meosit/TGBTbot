package com.tgbt.misc

import com.tgbt.BotOwnerIds
import com.tgbt.settings.Setting
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.output.TgTextOutput
import io.ktor.client.plugins.*
import io.ktor.client.statement.*
import io.ktor.server.application.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

val logger: Logger = LoggerFactory.getLogger("HelpersKt")

suspend inline fun <T> doNotThrow(message: String, block: () -> T?): T? = try {
    block()
} catch (e: Exception) {
    val response = (e as? ClientRequestException)?.response
    val clientError = (response?.bodyAsText() ?: e.message)?.ifBlank { "none" }?.escapeMarkdown() ?: "none"
    val textParameter =
        (response?.let { it.request.url.parameters["text"].orEmpty() }.orEmpty()).trimToLength(400, "|<- truncated")
            .ifBlank { "none" }.escapeMarkdown()
    val markdownText =
        "$message, please check logs, error message:\n`$clientError`\n\nText parameter (first 400 chars): `$textParameter`\n\n"
    logger.error(markdownText, e)
    val output = TgTextOutput(markdownText)
    BotOwnerIds.forEach { TelegramClient.sendChatMessage(it, output) }
    null
}

fun Application.launchScheduledRoutine(delayMinutesSetting: Setting, processName: String, func: suspend () -> Unit) {
    launch {
        do {
            try {
                func()
                val delayMinutes = delayMinutesSetting.long()
                logger.info("Next post forward check after $delayMinutes minutes")
                val delayMillis = TimeUnit.MINUTES.toMillis(delayMinutes)
                delay(delayMillis)
            } catch (e: Exception) {
                val message =
                    "Unexpected error occurred while $processName, next try in 60 seconds, error message:\n`${e.message?.escapeMarkdown()}`"
                logger.error(message, e)
                (e as? ClientRequestException)?.response?.bodyAsText()?.let { logger.error(it) }
                val output = TgTextOutput(message)
                BotOwnerIds.forEach { TelegramClient.sendChatMessage(it, output) }
                delay(60000)
            }
        } while (true)
    }
}
