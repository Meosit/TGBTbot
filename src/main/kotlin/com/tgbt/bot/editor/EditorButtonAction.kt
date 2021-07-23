package com.tgbt.bot.editor

import com.tgbt.bot.BotContext
import com.tgbt.bot.user.UserMessages
import com.tgbt.misc.trimToLength
import com.tgbt.post.TgPreparedPost
import com.tgbt.sendTelegramPost
import com.tgbt.settings.Setting
import com.tgbt.suggestion.SuggestionStatus
import com.tgbt.suggestion.UserSuggestion
import com.tgbt.suggestion.authorReference
import com.tgbt.telegram.CallbackQuery
import com.tgbt.telegram.InlineKeyboardButton
import com.tgbt.telegram.InlineKeyboardMarkup
import com.tgbt.telegram.Message
import com.tgbt.telegram.output.TgTextOutput
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant

object EditorButtonAction {
    private val scheduleDelays = mapOf(
        Duration.ofMinutes(30) to "30 минут",
        Duration.ofHours(1) to "1 час",
        Duration.ofHours(2) to "2 часа",
        Duration.ofHours(4) to "4 часа",
        Duration.ofHours(6) to "6 часов",
        Duration.ofHours(8) to "8 часов"
    )
    private const val DELETE_ACTION_DATA = "del"
    private const val CONFIRM_DELETE_ACTION_DATA = "del_confirm"
    private const val POST_ANONYMOUSLY_DATA = "anon"
    private const val CONFIRM_POST_ANONYMOUSLY_DATA = "anon_confirm"
    private const val SCHEDULE_POST_ANONYMOUSLY_DATA = "anon_sch_"
    private const val POST_PUBLICLY_DATA = "deanon"
    private const val CONFIRM_POST_PUBLICLY_DATA = "deanon_confirm"
    private const val SCHEDULE_POST_PUBLICLY_DATA = "deanon_sch_"

    private const val CANCEL_DATA = "cancel"
    private const val DELETED_DATA = "deleted"

    val ACTION_KEYBOARD = InlineKeyboardMarkup(listOf(
        listOf(InlineKeyboardButton("❌ Удалить пост", DELETE_ACTION_DATA)),
        listOf(
            InlineKeyboardButton("✅ Анонимно", POST_ANONYMOUSLY_DATA),
            InlineKeyboardButton("☑️ Не анонимно", POST_PUBLICLY_DATA)
        )
    ))

    suspend fun handleActionCallback(bot: BotContext, callback: CallbackQuery) = with(bot) {
        if (callback.data == DELETED_DATA) {
            tgMessageSender.pingCallbackQuery(callback.id)
            return
        }
        val message: Message? = callback.message
        if (message == null) {
            tgMessageSender.pingCallbackQuery(callback.id,
                "Сообщение устарело и недоступно боту, надо постить вручную")
            return
        }
        val suggestion = suggestionStore.findByChatAndMessageId(message.chat.id, message.id, byAuthor = false)
        when(callback.data) {
            DELETE_ACTION_DATA -> sendConfirmDialog(message, callback,
                InlineKeyboardButton("❌ Точно удалить?", CONFIRM_DELETE_ACTION_DATA), {})
            POST_ANONYMOUSLY_DATA -> sendConfirmDialog(message, callback,
                InlineKeyboardButton("✅ Точно отправить анонимно?", CONFIRM_POST_ANONYMOUSLY_DATA),
                scheduleButtons(SCHEDULE_POST_ANONYMOUSLY_DATA))
            POST_PUBLICLY_DATA -> sendConfirmDialog(message, callback,
                InlineKeyboardButton("☑️ Точно отправить с именем?", CONFIRM_POST_PUBLICLY_DATA),
                scheduleButtons(SCHEDULE_POST_PUBLICLY_DATA))
            CONFIRM_DELETE_ACTION_DATA -> {
                if (suggestion?.editorChatId != null && suggestion.editorMessageId != null) {
                    suggestionStore.removeByChatAndMessageId(suggestion.editorChatId, suggestion.editorMessageId, byAuthor = false)
                    if (settings[Setting.SEND_DELETION_FEEDBACK].toBoolean()) {
                        tgMessageSender.sendChatMessage(suggestion.authorChatId.toString(),
                            TgTextOutput(UserMessages.postDiscardedMessage.format(suggestion.postText.trimToLength(20, "..."))))
                    }
                }
                sendDeletedConfirmation(message, callback, "❌ Удалён ${callback.userRef()} ❌")
            }
            CONFIRM_POST_PUBLICLY_DATA -> sendSuggestion(suggestion, message, callback, anonymous = false)
            CONFIRM_POST_ANONYMOUSLY_DATA -> sendSuggestion(suggestion, message, callback, anonymous = true)
            CANCEL_DATA -> {
                val keyboardJson = json.stringify(InlineKeyboardMarkup.serializer(), ACTION_KEYBOARD)
                tgMessageSender.editChatMessageKeyboard(message.chat.id.toString(), message.id, keyboardJson)
                tgMessageSender.pingCallbackQuery(callback.id, "Действие отменено")
            }
            else -> when {
                suggestion != null && callback.data?.validSchedulePayload(SCHEDULE_POST_ANONYMOUSLY_DATA) == true ->
                    scheduleSuggestion(callback.data, suggestion, callback, message, anonymous = true)
                suggestion != null && callback.data?.validSchedulePayload(SCHEDULE_POST_PUBLICLY_DATA) == true ->
                    scheduleSuggestion(callback.data, suggestion, callback, message, anonymous = false)
                else -> tgMessageSender.pingCallbackQuery(callback.id, "Нераспознанные данные '${callback.data}'")
            }
        }
    }

    private suspend fun BotContext.scheduleSuggestion(
        data: String,
        suggestion: UserSuggestion,
        callback: CallbackQuery,
        message: Message,
        anonymous: Boolean
    ) {
        val prefix = if (anonymous) SCHEDULE_POST_ANONYMOUSLY_DATA else SCHEDULE_POST_PUBLICLY_DATA
        val status = if (anonymous) SuggestionStatus.SCHEDULE_ANONYMOUSLY else SuggestionStatus.SCHEDULE_PUBLICLY
        val confirm = if (anonymous) CONFIRM_POST_ANONYMOUSLY_DATA else CONFIRM_POST_PUBLICLY_DATA
        val emoji = if (anonymous) "✅" else "☑️"

        val duration = Duration
            .ofMinutes(data.removePrefix(prefix).toLong())
        val scheduleInstant = Instant.now().plus(duration)
        val updated = suggestion.copy(scheduleTime = Timestamp.from(scheduleInstant), status = status)
        suggestionStore.update(updated, byAuthor = false)
        val buttonLabel = "⌛️ Отложен ${callback.userRef()} на ${scheduleDelays[duration]} ⌛️"
        sendDeletedConfirmation(message, callback, buttonLabel,
            InlineKeyboardButton("$emoji Отправить прямо сейчас", confirm))
    }

    private fun String.validSchedulePayload(prefix: String) = this.startsWith(prefix)
            && this.removePrefix(prefix).toLongOrNull() != null
            && Duration.ofMinutes(this.removePrefix(prefix).toLong()) in scheduleDelays

    private suspend fun BotContext.sendSuggestion(
        suggestion: UserSuggestion?,
        message: Message,
        callback: CallbackQuery,
        anonymous: Boolean
    ) {
        if (suggestion?.editorChatId != null && suggestion.editorMessageId != null) {
            val channel = settings[Setting.TARGET_CHANNEL]
            val footerMd = settings[Setting.FOOTER_MD]
            val post = TgPreparedPost(
                suggestion.postText, suggestion.imageId, footerMarkdown = footerMd,
                suggestionReference = suggestion.authorReference(anonymous)
            )
            sendTelegramPost(channel, post)
            suggestionStore.removeByChatAndMessageId(suggestion.editorChatId, suggestion.editorMessageId, byAuthor = false)
            val emoji = if (anonymous) "✅" else "☑️"
            sendDeletedConfirmation(message, callback, "$emoji Опубликован ${callback.userRef()} $emoji")
            if (settings[Setting.SEND_PROMOTION_FEEDBACK].toBoolean()) {
                tgMessageSender.sendChatMessage(suggestion.authorChatId.toString(),
                    TgTextOutput(UserMessages.postPromotedMessage.format(suggestion.postText.trimToLength(20, "..."))))
            }
        } else {
            sendDeletedConfirmation(message, callback, "❔ Пост не найден ❔")
        }
    }

    private fun CallbackQuery.userRef() =  from.username?.let { "@$it" } ?: from.firstName

    private suspend fun BotContext.sendConfirmDialog(
        message: Message,
        callback: CallbackQuery,
        actionButton: InlineKeyboardButton,
        additionalButtons: suspend SequenceScope<List<InlineKeyboardButton>>.() -> Unit
    ) {
        val inlineKeyboard = sequence<List<InlineKeyboardButton>> {
            yield(listOf(actionButton))
            additionalButtons()
            yield(listOf(InlineKeyboardButton("↩️ Отмена действия", CANCEL_DATA)))
        }.toList()
        val inlineKeyboardMarkup = InlineKeyboardMarkup(inlineKeyboard)
        val keyboardJson = json.stringify(InlineKeyboardMarkup.serializer(), inlineKeyboardMarkup)
        tgMessageSender.editChatMessageKeyboard(message.chat.id.toString(), message.id, keyboardJson)
        tgMessageSender.pingCallbackQuery(callback.id)
    }

    private suspend fun scheduleButtons(payloadPrefix: String):
            suspend SequenceScope<List<InlineKeyboardButton>>.() -> Unit = {
        val buttons = scheduleDelays.map { (duration, label) ->
            InlineKeyboardButton("⌛️ Через $label", "$payloadPrefix${duration.toMinutes()}") }
        for (i in buttons.indices step 2) {
            yield((0 until 2).mapNotNull { buttons.getOrNull(it + i) })
        }
    }

    private suspend fun BotContext.sendDeletedConfirmation(
        message: Message,
        callback: CallbackQuery,
        buttonLabel: String,
        optionalAction: InlineKeyboardButton? = null
    ) {
        val inlineKeyboardMarkup = if (optionalAction == null) {
            InlineKeyboardMarkup(listOf(listOf(InlineKeyboardButton(buttonLabel, DELETED_DATA))))
        } else {
            InlineKeyboardMarkup(listOf(listOf(InlineKeyboardButton(buttonLabel, DELETED_DATA)), listOf(optionalAction)))
        }
        val keyboardJson = json.stringify(InlineKeyboardMarkup.serializer(), inlineKeyboardMarkup)
        tgMessageSender.editChatMessageKeyboard(message.chat.id.toString(), message.id, keyboardJson)
        tgMessageSender.pingCallbackQuery(callback.id, buttonLabel)
    }
}