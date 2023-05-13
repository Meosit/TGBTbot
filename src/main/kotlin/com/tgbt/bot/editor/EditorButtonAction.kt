package com.tgbt.bot.editor

import com.tgbt.BotJson
import com.tgbt.ban.BanStore
import com.tgbt.ban.UserBan
import com.tgbt.bot.user.UserMessages
import com.tgbt.doNotThrow
import com.tgbt.misc.escapeMarkdown
import com.tgbt.misc.isImageUrl
import com.tgbt.misc.simpleFormatTime
import com.tgbt.misc.trimToLength
import com.tgbt.post.TgPreparedPost
import com.tgbt.sendTelegramPost
import com.tgbt.settings.Setting
import com.tgbt.suggestion.*
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.api.*
import com.tgbt.telegram.output.TgTextOutput
import io.ktor.client.plugins.*
import io.ktor.http.*
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant

object EditorButtonAction {

    private val logger = LoggerFactory.getLogger("EditorButtonAction")

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
    private val rejectComments = mapOf(
        "ddos" to "Хватит уже это форсить",
        "notfun" to "Не смешно же",
        "endfail" to "Концовка слита",
        "format" to "Оформи нормально и перезалей",
        "fck" to "Пошёл нахуй с такими бугуртами",
        "pic" to "Прикрепи картинку и перезалей",
    )
    private val banComments = mapOf(
        "ddos" to "Заебал",
        "ddos2" to "Спамить нельзя",
        "over" to "Довыебывался",
        "fck" to "Ну и иди в бан",
        "calm" to "Посиди в бане, подумай над тем что скинул",
        "shame" to "Твоим родителям должно быть стыдно"
    )
    private val editComments = mapOf(
        "default" to "\uD83D\uDDBC Дефолтный йоба",
        "rage" to "\uD83D\uDDBC Горелый йоба",
        "cry" to "\uD83D\uDDBC Слезливый йоба",
        "creep" to "\uD83D\uDDBC Криповый йоба",
        "doom" to "\uD83D\uDDBC Думер йоба",
        "twist" to "\uD83D\uDDBC Скрюченый йоба",
        "prefix" to "❌\uD83D\uDDD1 Вступление",
        "postfix" to "❌\uD83D\uDDD1 Послесловие",
        "upper" to "\uD83E\uDE84 Оформить текст \uD83E\uDE84",
    )
    private val editActions = mapOf<String, (UserSuggestion) -> UserSuggestion>(
        "default" to { it.copy(imageId = "https://sun6.userapi.com/sun6-23/s/v1/ig2/Rw4gx5hgWBRBNpIfU6cSYDt07noe1MnICMR5BBLDfe9OKujnGAy7QAHtv3QnvvBUuo0DBjc9YZwbXrUWjJoNlFxU.jpg?size=551x551&quality=96&type=album") },
        "rage" to { it.copy(imageId = "https://sun9-north.userapi.com/sun9-82/s/v1/ig2/MaNAvmjCaJR-WmE9-hTg-2JtW7UylT0TywvTYJl2CZMKfBvbqSbAsNI3-JWdFbAZhz-RYfkRNtG59OCgxOYFldEH.jpg?size=500x500&quality=95&type=album") },
        "cry" to { it.copy(imageId = "https://sun6.userapi.com/sun6-20/s/v1/ig2/geSQmOIvkC_wJWs22X-mk63pbUV3h7Jhbk63EifU1dvf6w6PkN3mRZr8X2VjM3TtGkquon8FDDzYNTWp6YiEEKFh.jpg?size=521x500&quality=95&type=album") },
        "creep" to { it.copy(imageId = "https://sun9-north.userapi.com/sun9-86/s/v1/if1/CECJKh38jpVAPrDqtZLg_OscWYZMmevrSk-duCX9fOLLpwqjNLaJPMM2XIVOcSEas0TlKvZf.jpg?size=736x736&quality=96&type=album") },
        "doom" to { it.copy(imageId = "https://sun9-north.userapi.com/sun9-77/s/v1/ig2/hw2uLGMctR2RdMtqAYMmhD8aRWABELT4QpahlFapY1EtN7PmjlP5N2KMdqA4m3nACxFJMTRZfRit_JFa_RNIYnus.jpg?size=500x500&quality=96&type=album") },
        "twist" to { it.copy(imageId = "https://sun6.userapi.com/sun6-23/s/v1/ig2/enK9f6D-j_7fXjBEYNaoujBGmWxP-R6ZqKZrq0UXFra1GH1gH6rrCttREQn3n7NSbCT8hglYe6LKhrrPQTUn1pxr.jpg?size=604x604&quality=95&type=album") },
        "prefix" to { it.copy(postText = it.postText.removeBugurtSurrounding(prefix = true)) },
        "postfix" to { it.copy(postText = it.postText.removeBugurtSurrounding(prefix = false)) },
        "upper" to { it.copy(postText = it.postText.prettifyBugurt()) },
        )
    private const val DELETE_ACTION_DATA = "del"
    private const val DELETE_WITH_COMMENT_DATA = "del_comment_"
    private const val CONFIRM_DELETE_ACTION_DATA = "del_confirm"
    private const val EDIT_ACTION_DATA = "edit"
    private const val EDIT_DATA = "edit_"
    private const val BAN_ACTION_DATA = "ban"
    private const val BAN_WITH_COMMENT_DATA = "ban_"
    private const val POST_ANONYMOUSLY_DATA = "anon"
    private const val CONFIRM_POST_ANONYMOUSLY_DATA = "anon_confirm"
    private const val SCHEDULE_POST_ANONYMOUSLY_DATA = "anon_sch_"
    private const val POST_PUBLICLY_DATA = "deanon"
    private const val CONFIRM_POST_PUBLICLY_DATA = "deanon_confirm"
    private const val SCHEDULE_POST_PUBLICLY_DATA = "deanon_sch_"

    private const val CANCEL_DATA = "cancel"
    internal const val DELETED_DATA = "deleted"

    val ACTION_KEYBOARD = InlineKeyboardMarkup(listOf(
        listOf(
            InlineKeyboardButton("✏️ Редактировать", EDIT_ACTION_DATA),
        ),
        listOf(
            InlineKeyboardButton("❌ Удалить", DELETE_ACTION_DATA),
            InlineKeyboardButton("\uD83D\uDEAB Забанить", BAN_ACTION_DATA)
        ),
        listOf(
            InlineKeyboardButton("✅ Анонимно", POST_ANONYMOUSLY_DATA),
            InlineKeyboardButton("☑️ Не анонимно", POST_PUBLICLY_DATA)
        )
    ))

    suspend fun handleActionCallback(callback: CallbackQuery) {
        if (callback.data == DELETED_DATA) {
            TelegramClient.pingCallbackQuery(callback.id)
            return
        }
        val message: Message? = callback.message
        if (message == null) {
            TelegramClient.pingCallbackQuery(callback.id,
                "Сообщение устарело и недоступно боту, надо постить вручную")
            return
        }
        val suggestion = SuggestionStore.findByChatAndMessageId(message.chat.id, message.id, byAuthor = false)
        when(callback.data) {
            DELETE_ACTION_DATA -> sendConfirmDialog(message, callback,
                InlineKeyboardButton("❌ Удалить без комментария", CONFIRM_DELETE_ACTION_DATA), rejectPlaceholders())
            EDIT_ACTION_DATA -> sendConfirmDialog(message, callback, null, editPlaceholders())
            BAN_ACTION_DATA -> sendConfirmDialog(message, callback, null, banPlaceholders())
            POST_ANONYMOUSLY_DATA -> sendConfirmDialog(message, callback,
                InlineKeyboardButton("✅ Точно отправить анонимно?", CONFIRM_POST_ANONYMOUSLY_DATA),
                scheduleButtons(SCHEDULE_POST_ANONYMOUSLY_DATA))
            POST_PUBLICLY_DATA -> sendConfirmDialog(message, callback,
                InlineKeyboardButton("☑️ Точно отправить с именем?", CONFIRM_POST_PUBLICLY_DATA),
                scheduleButtons(SCHEDULE_POST_PUBLICLY_DATA))
            CONFIRM_DELETE_ACTION_DATA -> rejectPost(suggestion, message, callback)
            CONFIRM_POST_PUBLICLY_DATA -> sendSuggestion(suggestion, message, callback, anonymous = false)
            CONFIRM_POST_ANONYMOUSLY_DATA -> sendSuggestion(suggestion, message, callback, anonymous = true)
            CANCEL_DATA -> {
                if (suggestion != null) {
                    val keyboardJson = BotJson.encodeToString(InlineKeyboardMarkup.serializer(), ACTION_KEYBOARD)
                    if (suggestion.scheduleTime != null) {
                        val updated = suggestion.copy(scheduleTime = null, status = SuggestionStatus.PENDING_EDITOR_REVIEW)
                        SuggestionStore.update(updated, byAuthor = false)
                    }
                    TelegramClient.editChatMessageKeyboard(message.chat.id.toString(), message.id, keyboardJson)
                    TelegramClient.pingCallbackQuery(callback.id, "Действие отменено")
                } else {
                    sendPostNotFound(message, callback)
                }
            }
            else -> when {
                suggestion != null && callback.data?.validSchedulePayload(SCHEDULE_POST_ANONYMOUSLY_DATA) == true ->
                    scheduleSuggestion(callback.data, suggestion, callback, message, anonymous = true)
                suggestion != null && callback.data?.validSchedulePayload(SCHEDULE_POST_PUBLICLY_DATA) == true ->
                    scheduleSuggestion(callback.data, suggestion, callback, message, anonymous = false)
                suggestion != null && callback.data?.validRejectWithCommentPayload() == true ->
                    rejectPost(suggestion, message, callback, rejectComments[callback.data.removePrefix(DELETE_WITH_COMMENT_DATA)])
                suggestion != null && callback.data?.validBanWithCommentPayload() == true ->
                    banPost(suggestion, message, callback, banComments.getValue(callback.data.removePrefix(BAN_WITH_COMMENT_DATA)))
                suggestion != null && callback.data?.validEditPayload() == true ->
                    editSuggestion(suggestion, message, callback, editActions.getValue(callback.data.removePrefix(EDIT_DATA)))
                else -> TelegramClient.pingCallbackQuery(callback.id, "Нераспознанные данные '${callback.data}'")
            }
        }
    }

    private suspend fun rejectPost(
        suggestion: UserSuggestion?,
        message: Message,
        callback: CallbackQuery,
        rejectComment: String? = null
    ) = doNotThrow("Failed to send rejected port") {
        if (suggestion?.editorChatId != null && suggestion.editorMessageId != null) {
            val actuallyDeleted = SuggestionStore.removeByChatAndMessageId(suggestion.editorChatId, suggestion.editorMessageId, byAuthor = false)
            if (actuallyDeleted) {
                if (Setting.SEND_DELETION_FEEDBACK.bool()) {
                    val outputMessage = if (rejectComment != null) {
                        UserMessages.postDiscardedWithCommentMessage.format(suggestion.postTextTeaser().escapeMarkdown(), rejectComment.escapeMarkdown())
                    } else {
                        UserMessages.postDiscardedMessage.format(suggestion.postTextTeaser().escapeMarkdown())
                    }
                    try {
                        TelegramClient.sendChatMessage(suggestion.authorChatId.toString(), TgTextOutput(outputMessage))
                    } catch (e: ClientRequestException) {
                        if (e.response.status == HttpStatusCode.Forbidden) {
                            logger.info("Skipping deletion feedback for user ${suggestion.authorName} (${suggestion.authorChatId}): FORBIDDEN")
                        } else {
                            throw e
                        }
                    }
                }
                val commentPreview = if (rejectComment != null) " \uD83D\uDCAC $rejectComment" else ""
                sendDeletedConfirmation(message, callback,
                    "❌ Удалён ${callback.userRef()} в ${Instant.now().simpleFormatTime()}$commentPreview ❌".trimToLength(512, "…"))
                logger.info("Editor ${message.from?.simpleRef} rejected post '${suggestion.postTextTeaser()}' from ${suggestion.authorName} with comment '$rejectComment'")
            } else {
                sendPostNotFound(message, callback)
            }
        } else {
            sendPostNotFound(message, callback)
        }
    }

    private suspend fun banPost(
        suggestion: UserSuggestion,
        message: Message,
        callback: CallbackQuery,
        banComment: String
    ) = doNotThrow("Failed to send rejected port") {
        if (suggestion.editorChatId != null && suggestion.editorMessageId != null) {
            if (BanStore.findByChatId(suggestion.authorChatId) == null) {
                val ban = UserBan(
                    authorChatId = suggestion.authorChatId,
                    authorName = suggestion.authorName,
                    postTeaser = suggestion.postTextTeaser(),
                    reason = banComment,
                    bannedBy = callback.from.simpleRef
                )
                BanStore.insert(ban)
                logger.info("User ${ban.authorName} was banned by ${ban.bannedBy}")
            }
            val actuallyDeleted = SuggestionStore.removeByChatAndMessageId(suggestion.editorChatId, suggestion.editorMessageId, byAuthor = false)
            if (actuallyDeleted) {
                TelegramClient.sendChatMessage(suggestion.authorChatId.toString(), TgTextOutput(UserMessages.bannedErrorMessage
                    .format(suggestion.postTextTeaser().escapeMarkdown(), banComment.escapeMarkdown())))
                val keyboardJson = BotJson.encodeToString(
                    InlineKeyboardMarkup.serializer(),
                    InlineKeyboardButton("\uD83D\uDEAB Забанен ${callback.from.simpleRef} в ${Instant.now().simpleFormatTime()} \uD83D\uDCAC $banComment ❌".trimToLength(512, "…"), DELETED_DATA).toMarkup())
                TelegramClient.editChatMessageKeyboard(suggestion.editorChatId.toString(), suggestion.editorMessageId, keyboardJson)
                logger.info("Editor ${message.from?.simpleRef} banned a user ${suggestion.authorName} because of post '${suggestion.postTextTeaser()}', comment '$banComment'")
            }
        } else {
            sendPostNotFound(message, callback)
        }
    }

    private suspend fun scheduleSuggestion(
        data: String,
        suggestion: UserSuggestion,
        callback: CallbackQuery,
        message: Message,
        anonymous: Boolean
    ) = doNotThrow("Failed to schedule suggestion") {
        val prefix = if (anonymous) SCHEDULE_POST_ANONYMOUSLY_DATA else SCHEDULE_POST_PUBLICLY_DATA
        val status = if (anonymous) SuggestionStatus.SCHEDULE_ANONYMOUSLY else SuggestionStatus.SCHEDULE_PUBLICLY
        val confirm = if (anonymous) CONFIRM_POST_ANONYMOUSLY_DATA else CONFIRM_POST_PUBLICLY_DATA
        val emoji = if (anonymous) "✅" else "☑️"

        val duration = Duration
            .ofMinutes(data.removePrefix(prefix).toLong())
        val scheduleInstant = Instant.now().plus(duration)
        val updated = suggestion.copy(scheduleTime = Timestamp.from(scheduleInstant), status = status)
        SuggestionStore.update(updated, byAuthor = false)
        val scheduleLabel = scheduleInstant.simpleFormatTime()
        val buttonLabel = "⌛️ Отложен ${callback.userRef()} на ≈$scheduleLabel ⌛️"
        sendDeletedConfirmation(message, callback, buttonLabel,
            listOf(InlineKeyboardButton("$emoji Прямо сейчас", confirm), InlineKeyboardButton("↩️ Отмена действия", CANCEL_DATA)))
        logger.info("Editor ${message.from?.simpleRef} scheduled post '${suggestion.postTextTeaser()}' from ${suggestion.authorName} to $scheduleLabel")
    }


    private val lowercaseBugurtPartsRegex = "#[a-zа-я_]+|\\*.*?\\*|<.*?>".toRegex(RegexOption.IGNORE_CASE)
    private val bugurtRegex = "(@\\n?)?([^@\\n]+\\n?(@\\s*\\n?)+)+[^@\\n]+(\\n?@)?".toRegex(RegexOption.MULTILINE)

    private fun String.prettifyBugurt(): String = this.replace(bugurtRegex) { match -> match.value
        .trim()
        .dropWhile { it == '@' }
        .dropLastWhile { it == '@' }
        .split("@")
        .joinToString("\n@") { line -> when(line) {
            "", "\n" -> ""
            else ->  "\n" + line.trim().uppercase().replace(lowercaseBugurtPartsRegex) { it.value.lowercase() } }
        }.trim()
    }

    private fun String.removeBugurtSurrounding(prefix: Boolean): String {
        val match = bugurtRegex.find(this)
        if (match != null) {
            return if (prefix) this.removeRange(0, match.range.first)
            else this.removeRange(match.range.last + 1, this.length)
        }
        return this
    }

    private suspend fun editSuggestion(
        suggestion: UserSuggestion,
        message: Message,
        callback: CallbackQuery,
        editAction: (UserSuggestion) -> UserSuggestion
    ) = doNotThrow("Failed to edit suggestion") {
        val updated = editAction(suggestion)
        val keyboardJson = BotJson.encodeToString(InlineKeyboardMarkup.serializer(), ACTION_KEYBOARD)
        SuggestionStore.update(updated, byAuthor = false)

        // footer links should not be previewed.
        val post = TgPreparedPost(updated.postText, updated.imageId,
            Setting.FOOTER_MD.str(), suggestion.authorReference(false))

        if (message.photo != null) {
            if (updated.imageId != suggestion.imageId) {
                val imageUrl = post.maybeImage ?: message.photo.first().fileId
                TelegramClient.editChatMessagePhoto(message.chat.id.toString(), message.id, imageUrl)
            }
            val caption = post.withoutImage.trimToLength(1024, "...\n_(пост стал длиннее чем 1024 символа)_")
            TelegramClient.editChatMessageCaption(message.chat.id.toString(), message.id, caption, keyboardJson)
        } else {
            val disableLinkPreview = post.footerMarkdown.contains("https://")
                    && !post.text.contains("https://")
                    && !(post.maybeImage?.isImageUrl() ?: false)
            TelegramClient.editChatMessageText(
                message.chat.id.toString(),
                message.id,
                post.withImage,
                keyboardJson,
                disableLinkPreview = disableLinkPreview
            )
        }

        TelegramClient.pingCallbackQuery(callback.id, "✏️✅ Пост изменен ✏️✅")
        logger.info("Editor ${message.from?.simpleRef} updated post '${suggestion.postTextTeaser()}' from ${suggestion.authorName}")
    }

    private fun String.validSchedulePayload(prefix: String) = this.startsWith(prefix)
            && this.removePrefix(prefix).toLongOrNull() != null
            && Duration.ofMinutes(this.removePrefix(prefix).toLong()) in scheduleDelays

    private fun String.validRejectWithCommentPayload() = this.startsWith(DELETE_WITH_COMMENT_DATA)
            && this.removePrefix(DELETE_WITH_COMMENT_DATA) in rejectComments

    private fun String.validBanWithCommentPayload() = this.startsWith(BAN_WITH_COMMENT_DATA)
            && this.removePrefix(BAN_WITH_COMMENT_DATA) in banComments

    private fun String.validEditPayload() = this.startsWith(EDIT_DATA)
            && this.removePrefix(EDIT_DATA) in editActions

    private suspend fun sendSuggestion(
        suggestion: UserSuggestion?,
        message: Message,
        callback: CallbackQuery,
        anonymous: Boolean
    ) = doNotThrow("Failed to post suggestion") {
        if (suggestion?.editorChatId != null && suggestion.editorMessageId != null) {
            val channel = Setting.TARGET_CHANNEL.str()
            val footerMd = Setting.FOOTER_MD.str()
            val post = TgPreparedPost(
                suggestion.postText, suggestion.imageId, footerMarkdown = footerMd,
                suggestionReference = suggestion.authorReference(anonymous)
            )
            sendTelegramPost(channel, post)
            SuggestionStore.removeByChatAndMessageId(suggestion.editorChatId, suggestion.editorMessageId, byAuthor = false)
            val emoji = if (anonymous) "✅" else "☑️"
            sendDeletedConfirmation(message, callback, "$emoji Опубликован ${callback.userRef()} в ${Instant.now().simpleFormatTime()} $emoji")
            if (Setting.SEND_PROMOTION_FEEDBACK.bool()) {
                try {
                    TelegramClient.sendChatMessage(suggestion.authorChatId.toString(),
                        TgTextOutput(UserMessages.postPromotedMessage.format(suggestion.postTextTeaser()).escapeMarkdown()))
                } catch (e: ClientRequestException) {
                    if (e.response.status == HttpStatusCode.Forbidden) {
                        logger.info("Skipping promotion feedback for user ${suggestion.authorName} (${suggestion.authorChatId}): FORBIDDEN")
                    } else {
                        throw e
                    }
                }
            }
            logger.info("Editor ${message.from?.simpleRef} promoted post '${suggestion.postTextTeaser()}' from ${suggestion.authorName}")
        } else {
            sendPostNotFound(message, callback)
        }
    }

    private suspend fun sendPostNotFound(
        message: Message,
        callback: CallbackQuery
    ) {
        val firstButtonLabel = message.replyMarkup?.inlineKeyboard?.getOrNull(0)?.getOrNull(0)?.text
        val anonymous = message.replyMarkup?.inlineKeyboard?.getOrNull(1)?.getOrNull(0)?.text?.contains("☑️") == false
        // handling action on already forwarded via schedule post - removing any action buttons
        val buttonLabel = if (firstButtonLabel != null && firstButtonLabel.contains("⌛️")) {
            val emoji = if (anonymous) "✅" else "☑️"
            firstButtonLabel.replace("Отложен", "Уже опубликован").replaceFirst("⌛", emoji)
        } else {
            "❔ Пост не найден ❔"
        }
        sendDeletedConfirmation(message, callback, buttonLabel)
        logger.info("Editor ${message.from?.simpleRef} tried to do something with deleted post")
    }

    private fun CallbackQuery.userRef() =  from.simpleRef

    private suspend fun sendConfirmDialog(
        message: Message,
        callback: CallbackQuery,
        actionButton: InlineKeyboardButton?,
        additionalButtons: suspend SequenceScope<List<InlineKeyboardButton>>.() -> Unit
    ) {
        val inlineKeyboard = sequence {
            if (actionButton != null) {
                yield(listOf(actionButton))
            }
            additionalButtons()
            yield(listOf(InlineKeyboardButton("↩️ Отмена действия", CANCEL_DATA)))
        }.toList()
        val inlineKeyboardMarkup = InlineKeyboardMarkup(inlineKeyboard)
        val keyboardJson = BotJson.encodeToString(InlineKeyboardMarkup.serializer(), inlineKeyboardMarkup)
        TelegramClient.editChatMessageKeyboard(message.chat.id.toString(), message.id, keyboardJson)
        TelegramClient.pingCallbackQuery(callback.id)
    }

    private suspend fun scheduleButtons(payloadPrefix: String):
            suspend SequenceScope<List<InlineKeyboardButton>>.() -> Unit = {
        val buttons = scheduleDelays.map { (duration, label) ->
            InlineKeyboardButton("⌛️ Через $label", "$payloadPrefix${duration.toMinutes()}") }
        for (i in buttons.indices step 2) {
            yield((0 until 2).mapNotNull { buttons.getOrNull(it + i) })
        }
    }

    private suspend fun rejectPlaceholders(): suspend SequenceScope<List<InlineKeyboardButton>>.() -> Unit = { rejectComments
            .map { (key, comment) -> InlineKeyboardButton("❌ \uD83D\uDCAC \"$comment\" ❌", "$DELETE_WITH_COMMENT_DATA$key") }
            .forEach { yield(listOf(it)) }
    }

    private suspend fun banPlaceholders(): suspend SequenceScope<List<InlineKeyboardButton>>.() -> Unit = { banComments
            .map { (key, comment) -> InlineKeyboardButton("❌ \uD83D\uDCAC \"$comment\" ❌", "$BAN_WITH_COMMENT_DATA$key") }
            .forEach { yield(listOf(it)) }
    }

    private suspend fun editPlaceholders(): suspend SequenceScope<List<InlineKeyboardButton>>.() -> Unit = {
        val buttons = editComments.map { (key, title) -> InlineKeyboardButton(title, "$EDIT_DATA$key") }
        for (i in buttons.indices step 2) {
            yield((0 until 2).mapNotNull { buttons.getOrNull(i + it) })
        }
    }

    private suspend fun sendDeletedConfirmation(
        message: Message,
        callback: CallbackQuery,
        buttonLabel: String,
        optionalActions: List<InlineKeyboardButton>? = null
    ) {
        val inlineKeyboardMarkup = if (optionalActions == null) {
            InlineKeyboardButton(buttonLabel, DELETED_DATA).toMarkup()
        } else {
            InlineKeyboardMarkup(listOf(listOf(InlineKeyboardButton(buttonLabel, DELETED_DATA)), optionalActions))
        }
        val keyboardJson = BotJson.encodeToString(InlineKeyboardMarkup.serializer(), inlineKeyboardMarkup)
        TelegramClient.editChatMessageKeyboard(message.chat.id.toString(), message.id, keyboardJson)
        TelegramClient.pingCallbackQuery(callback.id, buttonLabel)
    }
}