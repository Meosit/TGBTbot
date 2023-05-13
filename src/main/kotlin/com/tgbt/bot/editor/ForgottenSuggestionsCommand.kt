package com.tgbt.bot.editor

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.misc.doNotThrow
import com.tgbt.misc.zonedNow
import com.tgbt.settings.Setting
import com.tgbt.suggestion.SuggestionStatus
import com.tgbt.suggestion.SuggestionStore
import com.tgbt.suggestion.UserSuggestion
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.output.TgTextOutput
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

object ForgottenSuggestionsCommand : BotCommand {
    override val command: String = "/forgotten"
    private val logger = LoggerFactory.getLogger("ForgottenSuggestionsCommand")

    override suspend fun MessageContext.handle() {
        val threshold = messageText.removePrefix(command).trim().toIntOrNull()?.takeIf { it in 0..1000 } ?: 0
        val forgotten = notifyAboutForgottenSuggestions(force = true, createdBeforeHours = threshold)
        TelegramClient.sendChatMessage(chatId, TgTextOutput("Проверка на забытые посты завершена, всего забытых постов: $forgotten"))
    }

    suspend fun notifyAboutForgottenSuggestions(force: Boolean = false, createdBeforeHours: Int = 0): Int {
        val start = LocalTime.of(0, 0, 0, 0)
        val end = LocalTime.of(0, Setting.SUGGESTION_POLLING_DELAY_MINUTES.int(), 0, 0)
        val now = zonedNow().toLocalTime()
        var forgotten = 0
        if (force || (now in start..end)) {
            if (!force) {
                logger.info("New day, checking for forgotten posts")
            }
            val forgottenSuggestions = doNotThrow("Failed to fetch PENDING_EDITOR_REVIEW suggestions from DB") {
                SuggestionStore.findByStatus(SuggestionStatus.PENDING_EDITOR_REVIEW)
            }
            logger.info("Currently there are ${forgottenSuggestions?.size} forgotten posts, notifying...")
            forgottenSuggestions?.forEach { suggestion ->
                doNotThrow("Failed to notify about forgotten post") {
                    val hoursSinceCreated = Duration
                        .between(Instant.now(), suggestion.insertedTime.toInstant()).abs().toHours()
                    if (hoursSinceCreated > createdBeforeHours) {
                        logger.info("Post from ${suggestion.authorName} created $hoursSinceCreated hours ago")
                        sendPendingReviewNotification(suggestion, hoursSinceCreated)
                        delay(3100)
                        forgotten++
                    }
                }
            }
        }
        return forgotten
    }

    private suspend fun sendPendingReviewNotification(
        suggestion: UserSuggestion,
        hoursSinceCreated: Long
    ) = TelegramClient.sendChatMessage(
        suggestion.editorChatId.toString(),
        TgTextOutput("Пост ждёт обработки, создан $hoursSinceCreated часов назад"),
        replyMessageId = suggestion.editorMessageId
    )

}
