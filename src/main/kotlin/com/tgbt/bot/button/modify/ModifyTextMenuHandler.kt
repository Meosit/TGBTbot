package com.tgbt.bot.button.modify

import com.tgbt.BotJson
import com.tgbt.bot.button.CallbackNotificationText
import com.tgbt.bot.button.MainMenuHandler
import com.tgbt.suggestion.UserSuggestion
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.api.InlineKeyboardButton
import com.tgbt.telegram.api.InlineKeyboardMarkup
import com.tgbt.telegram.api.Message

abstract class ModifyTextMenuHandler(
    category: String,
    private val searchByAuthor: Boolean,
    private val mainMenuHandler: MainMenuHandler
): ModifyMenuHandler(category, "M_TEXT", mainMenuHandler) {

    private val editComments = mapOf(
        "upper" to "\uD83E\uDE84 Оформить текст \uD83E\uDE84",
        "prefix" to "\uD83D\uDDD1 Удалить вступление \uD83D\uDDD1",
        "postfix" to "\uD83D\uDDD1 Удалить послесловие \uD83D\uDDD1",
    )
    private val editActions = mapOf<String, (UserSuggestion) -> UserSuggestion>(
        "upper" to { it.copy(postText = it.postText.prettifyBugurt()) },
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
    ) = modifyPost(message, searchByAuthor, editActions.getValue(validPayload))

    override suspend fun renderNewMenu(message: Message, pressedBy: String): CallbackNotificationText {
        val keyboard = sequence {
            editComments
                .map { (key, comment) -> InlineKeyboardButton(comment, callbackData(key)) }
                .forEach { yield(listOf(it)) }
            yield(listOf(mainMenuHandler.backButton))
        }.toList().let { InlineKeyboardMarkup(it) }
        val keyboardJson = BotJson.encodeToString(InlineKeyboardMarkup.serializer(), keyboard)
        TelegramClient.editChatMessageKeyboard(message.chat.id.toString(), message.id, keyboardJson)
        return null
    }
}