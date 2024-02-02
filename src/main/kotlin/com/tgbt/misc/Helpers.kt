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
val editorsVkNotificationLabel: List<String> = listOf(
    "Connect timeout has expired",
    "Socket timeout has expired",
    "502 Bad Gateway",
    "Internal server error",
    "api.vk.com: Name or service not known"
)

suspend inline fun <T> doNotThrow(message: String, block: () -> T?): T? = try {
    block()
} catch (e: Exception) {
    val stackPart = e.stackTraceToString().lineSequence()
        .filter { it.contains("com.tgbt") }.map { it.trim().escapeMarkdown() }.toList().takeLast(5)
        .joinToString("\n").ifBlank {
            logger.warn("NO BOT CODEBASE found in stacktrace:\n${e.stackTraceToString()}")
            "no bot codebase found"
        }
    val response = (e as? ClientRequestException)?.response
    val clientError = (response?.bodyAsText()?.ifBlank { "none" } ?: "[exp] ${e.message}").escapeMarkdown()
    val markdownText =
        "$message, please check logs, error message:\n`$clientError`\n\nStackTrace part:\n$stackPart"
    logger.error(markdownText, e)
    val output = TgTextOutput(markdownText)
    BotOwnerIds.forEach { TelegramClient.sendChatMessage(it, output) }

    if ("Failed to load or parse VK posts" in message) {
        val label = editorsVkNotificationLabel.find { it in clientError }
        if (label != null) {
            TelegramClient.sendChatMessage(Setting.EDITOR_CHAT_ID.str(),
                TgTextOutput("⚠\uFE0F VK API is not working ⚠\uFE0F\n\nTHIS IS AN EXTERNAL FAILURE, BOT IS WORKING AND NO ACTION NEEDED, JUST FOR YOUR INFORMATION")
            )
        }
    }
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
