package com.tgbt.bot.editor.button

import com.tgbt.BotJson
import com.tgbt.bot.CallbackNotificationText
import com.tgbt.bot.MenuHandler
import com.tgbt.bot.user.UserMessages
import com.tgbt.doNotThrow
import com.tgbt.misc.escapeMarkdown
import com.tgbt.misc.simpleFormatTime
import com.tgbt.post.TgPreparedPost
import com.tgbt.post.sendToTelegram
import com.tgbt.settings.Setting
import com.tgbt.suggestion.SuggestionStatus
import com.tgbt.suggestion.SuggestionStore
import com.tgbt.suggestion.authorReference
import com.tgbt.suggestion.postTextTeaser
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.api.InlineKeyboardButton
import com.tgbt.telegram.api.InlineKeyboardMarkup
import com.tgbt.telegram.api.Message
import com.tgbt.telegram.api.simpleRef
import com.tgbt.telegram.output.TgTextOutput
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant

sealed class PostMenuHandler(id: String, private val postEmoji: String, private val anonymous: Boolean) :
    MenuHandler("EDIT", id) {

    private val logger = LoggerFactory.getLogger(this::class.simpleName)

    object PostAnonymouslyMenuHandler : PostMenuHandler("ANON", "✅", true)
    object PostPubliclyMenuHandler : PostMenuHandler("DEANON", "☑️", false)

    private val postNowPayload = "now"
    private val cancelSchedulePayload = "cancel"

    private val scheduleEmoji = "⌛"
    private val scheduleDelays = mapOf(
        Duration.ofMinutes(30) to "30 минут",
        Duration.ofHours(1) to "1 час",
        Duration.ofHours(2) to "2 часа",
        Duration.ofHours(4) to "4 часа",
        Duration.ofHours(6) to "6 часов",
        Duration.ofHours(8) to "8 часов",
        Duration.ofHours(12) to "12 часов",
        Duration.ofHours(16) to "16 часов",
        Duration.ofHours(24) to "24 часа",
        Duration.ofHours(48) to "48 часов",
    )

    override fun isValidPayload(payload: String): Boolean = payload == postNowPayload || payload == cancelSchedulePayload ||
            (payload.toLongOrNull() != null && Duration.ofMinutes(payload.toLong()) in scheduleDelays)

    override suspend fun handleButtonAction(
        message: Message,
        pressedBy: String,
        validPayload: String
    ): CallbackNotificationText {
        val suggestion = SuggestionStore.findByChatAndMessageId(message.chat.id, message.id, byAuthor = false)
        return if (suggestion?.editorChatId != null && suggestion.editorMessageId != null) {
            when (validPayload) {
                postNowPayload -> {
                    doNotThrow("Failed to post suggestion") {
                        val channel = Setting.TARGET_CHANNEL.str()
                        val post = TgPreparedPost(
                            suggestion.postText, suggestion.imageId,
                            suggestionReference = suggestion.authorReference(anonymous)
                        )
                        post.sendToTelegram(channel)
                        SuggestionStore.removeByChatAndMessageId(
                            suggestion.editorChatId,
                            suggestion.editorMessageId,
                            byAuthor = false
                        )
                        TelegramClient.sendChatMessage(
                            suggestion.authorChatId.toString(),
                            TgTextOutput(
                                UserMessages.postPromotedMessage.format(suggestion.postTextTeaser()).escapeMarkdown()
                            )
                        )
                        logger.info("Editor ${message.from.simpleRef} promoted post '${suggestion.postTextTeaser()}' from ${suggestion.authorName}")
                        val buttonLabel = "$postEmoji Опубликован $pressedBy в ${Instant.now().simpleFormatTime()} $postEmoji"
                        FinishedMenuHandler(buttonLabel).handle(message, pressedBy, null)
                    }
                }
                cancelSchedulePayload -> {
                    if (suggestion.scheduleTime != null) {
                        val updated = suggestion.copy(scheduleTime = null, status = SuggestionStatus.PENDING_EDITOR_REVIEW)
                        SuggestionStore.update(updated, byAuthor = false)
                        MainMenuHandler.handle(message, pressedBy, null)
                    } else {
                        FinishedMenuHandler(postNotFoundMessage(message)).handle(message, pressedBy, null)
                    }
                }
                else -> {
                    doNotThrow("Failed to schedule suggestion") {
                        val duration = Duration.ofMinutes(validPayload.toLong())
                        val status = if (anonymous) SuggestionStatus.SCHEDULE_ANONYMOUSLY else SuggestionStatus.SCHEDULE_PUBLICLY
                        val scheduleInstant = Instant.now().plus(duration)
                        val updated = suggestion.copy(scheduleTime = Timestamp.from(scheduleInstant), status = status)
                        SuggestionStore.update(updated, byAuthor = false)
                        val scheduleLabel = scheduleInstant.simpleFormatTime()
                        val buttonLabel = "$scheduleEmoji Отложен $pressedBy на ≈$scheduleLabel $scheduleEmoji"
                        val additionalButtons = listOf(
                            InlineKeyboardButton("$postEmoji Прямо сейчас", callbackData(postNowPayload)),
                            InlineKeyboardButton("↩️ Отмена действия", callbackData(cancelSchedulePayload))
                        )
                        logger.info("Editor ${message.from.simpleRef} scheduled post '${suggestion.postTextTeaser()}' from ${suggestion.authorName} to $scheduleLabel")
                        FinishedMenuHandler(buttonLabel, additionalButtons).handle(message, pressedBy, null)
                    }
                }
            }
        } else {
            FinishedMenuHandler(postNotFoundMessage(message)).handle(message, pressedBy, null)
        }
    }

    private fun postNotFoundMessage(message: Message): String {
        val firstButtonLabel = message.replyMarkup?.inlineKeyboard?.getOrNull(0)?.getOrNull(0)?.text
        // handling action on already forwarded via schedule post - removing any action buttons
        return if (firstButtonLabel != null && firstButtonLabel.contains(scheduleEmoji)) {
            firstButtonLabel.replaceFirst(scheduleEmoji, postEmoji)
        } else {
            "❔ Пост не найден ❔"
        }
    }

    override suspend fun renderNewMenu(message: Message, pressedBy: String): CallbackNotificationText {
        val keyboard = sequence {
            yield(listOf(InlineKeyboardButton("$postEmoji Отправить сейчас", callbackData(postNowPayload))))
            val buttons = scheduleDelays.map { (duration, label) ->
                InlineKeyboardButton("$scheduleEmoji Через $label", callbackData(duration.toMinutes().toString()))
            }
            for (i in buttons.indices step 2) {
                yield((0 until 2).mapNotNull { buttons.getOrNull(it + i) })
            }
            yield(listOf(MainMenuHandler.BACK_TO_MAIN_BUTTON))
        }.toList().let { InlineKeyboardMarkup(it) }
        val keyboardJson = BotJson.encodeToString(InlineKeyboardMarkup.serializer(), keyboard)
        TelegramClient.editChatMessageKeyboard(message.chat.id.toString(), message.id, keyboardJson)
        return null
    }
}