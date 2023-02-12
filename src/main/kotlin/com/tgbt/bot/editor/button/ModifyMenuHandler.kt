package com.tgbt.bot.editor.button

import com.tgbt.BotJson
import com.tgbt.bot.CallbackNotificationText
import com.tgbt.bot.MenuHandler
import com.tgbt.misc.isImageUrl
import com.tgbt.misc.trimToLength
import com.tgbt.post.TgPreparedPost
import com.tgbt.suggestion.SuggestionStore
import com.tgbt.suggestion.UserSuggestion
import com.tgbt.suggestion.authorReference
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.api.InlineKeyboardButton
import com.tgbt.telegram.api.InlineKeyboardMarkup
import com.tgbt.telegram.api.Message

object ModifyMenuHandler: MenuHandler("EDIT", "MODIFY") {

    private const val noImagePlaceholder = "https://cdn.segmentnext.com/wp-content/themes/segmentnext/images/no-image-available.jpg"
    private val editComments = mapOf(
        "upper" to "\uD83E\uDE84 Оформить текст \uD83E\uDE84",
        "nopic" to "❌\uD83D\uDDD1 Картинку",
        "default" to "\uD83D\uDDBC Дефолтный йоба",
        "rage" to "\uD83D\uDDBC Горелый йоба",
        "cry" to "\uD83D\uDDBC Слезливый йоба",
        "creep" to "\uD83D\uDDBC Криповый йоба",
        "doom" to "\uD83D\uDDBC Думер йоба",
        "twist" to "\uD83D\uDDBC Скрюченый йоба",
        "prefix" to "❌\uD83D\uDDD1 Вступление",
        "postfix" to "❌\uD83D\uDDD1 Послесловие",
    )
    private val editActions = mapOf<String, (UserSuggestion) -> UserSuggestion>(
        "upper" to { it.copy(postText = it.postText.prettifyBugurt()) },
        "nopic" to { it.copy(imageId = null) },
        "default" to { it.copy(imageId = "https://sun6.userapi.com/sun6-23/s/v1/ig2/Rw4gx5hgWBRBNpIfU6cSYDt07noe1MnICMR5BBLDfe9OKujnGAy7QAHtv3QnvvBUuo0DBjc9YZwbXrUWjJoNlFxU.jpg?size=551x551&quality=96&type=album") },
        "rage" to { it.copy(imageId = "https://sun9-north.userapi.com/sun9-82/s/v1/ig2/MaNAvmjCaJR-WmE9-hTg-2JtW7UylT0TywvTYJl2CZMKfBvbqSbAsNI3-JWdFbAZhz-RYfkRNtG59OCgxOYFldEH.jpg?size=500x500&quality=95&type=album") },
        "cry" to { it.copy(imageId = "https://sun6.userapi.com/sun6-20/s/v1/ig2/geSQmOIvkC_wJWs22X-mk63pbUV3h7Jhbk63EifU1dvf6w6PkN3mRZr8X2VjM3TtGkquon8FDDzYNTWp6YiEEKFh.jpg?size=521x500&quality=95&type=album") },
        "creep" to { it.copy(imageId = "https://sun9-north.userapi.com/sun9-86/s/v1/if1/CECJKh38jpVAPrDqtZLg_OscWYZMmevrSk-duCX9fOLLpwqjNLaJPMM2XIVOcSEas0TlKvZf.jpg?size=736x736&quality=96&type=album") },
        "doom" to { it.copy(imageId = "https://sun9-north.userapi.com/sun9-77/s/v1/ig2/hw2uLGMctR2RdMtqAYMmhD8aRWABELT4QpahlFapY1EtN7PmjlP5N2KMdqA4m3nACxFJMTRZfRit_JFa_RNIYnus.jpg?size=500x500&quality=96&type=album") },
        "twist" to { it.copy(imageId = "https://sun6.userapi.com/sun6-23/s/v1/ig2/enK9f6D-j_7fXjBEYNaoujBGmWxP-R6ZqKZrq0UXFra1GH1gH6rrCttREQn3n7NSbCT8hglYe6LKhrrPQTUn1pxr.jpg?size=604x604&quality=95&type=album") },
        "prefix" to { it.copy(postText = it.postText.removeBugurtSurrounding(prefix = true)) },
        "postfix" to { it.copy(postText = it.postText.removeBugurtSurrounding(prefix = false)) },
    )

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

    override fun isValidPayload(payload: String): Boolean = payload in editComments

    override suspend fun handleButtonAction(
        message: Message,
        pressedBy: String,
        validPayload: String
    ): CallbackNotificationText {
        val suggestion = SuggestionStore.findByChatAndMessageId(message.chat.id, message.id, byAuthor = false)
        val editAction = editActions.getValue(validPayload)
        return if (suggestion?.editorChatId != null && suggestion.editorMessageId != null) {
            val updated = editAction(suggestion)
            val keyboardJson = BotJson.encodeToString(InlineKeyboardMarkup.serializer(), MainMenuHandler.ROOT_KEYBOARD)
            SuggestionStore.update(updated, byAuthor = false)

            // footer links should not be previewed.
            val post = TgPreparedPost(updated.postText, updated.imageId,
                suggestionReference = suggestion.authorReference(false))

            if (message.photo != null) {
                when {
                    updated.imageId != suggestion.imageId -> {
                        val imageUrl = post.maybeImage ?: message.photo.first().fileId
                        TelegramClient.editChatMessagePhoto(message.chat.id.toString(), message.id, imageUrl)
                    }
                    updated.imageId == null -> {
                        TelegramClient.editChatMessagePhoto(message.chat.id.toString(), message.id, noImagePlaceholder)
                    }
                }
                val caption = post.withoutImage.trimToLength(1024, "...\n_(пост стал длиннее 1024 символов, но отобразится корректно после публикации)_")
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
            return "✏️✅ Пост изменен ✏️✅"
        } else {
            FinishedMenuHandler("❔ Пост не найден ❔").handle(message, pressedBy, null)
        }
    }

    override suspend fun renderNewMenu(message: Message, pressedBy: String): CallbackNotificationText {
        val keyboard = sequence {
            val buttons = editComments.map { (key, comment) ->
                InlineKeyboardButton(comment, callbackData(key))
            }
            yield(listOf(buttons[0]))
            for (i in buttons.drop(1).indices step 2) {
                yield((0 until 2).mapNotNull { buttons.getOrNull(it + i) })
            }
            yield(listOf(MainMenuHandler.BACK_TO_MAIN_BUTTON))
            editComments
                .map { (key, comment) -> InlineKeyboardButton(comment, callbackData(key)) }
                .forEach { yield(listOf(it)) }
            yield(listOf(MainMenuHandler.BACK_TO_MAIN_BUTTON))
        }.toList().let { InlineKeyboardMarkup(it) }
        val keyboardJson = BotJson.encodeToString(InlineKeyboardMarkup.serializer(), keyboard)
        TelegramClient.editChatMessageKeyboard(message.chat.id.toString(), message.id, keyboardJson)
        return null
    }
}