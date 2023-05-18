package com.tgbt.bot.button.modify

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.bot.user.UserMessages
import com.tgbt.settings.Setting
import com.tgbt.suggestion.SuggestionStore
import com.tgbt.suggestion.UserSuggestion
import com.tgbt.suggestion.userCanEdit
import com.tgbt.suggestion.userNewPostSecondsRemaining
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.api.InlineKeyboardButton
import com.tgbt.telegram.api.InlineKeyboardMarkup
import com.tgbt.telegram.api.Message
import com.tgbt.telegram.output.TgTextOutput

abstract class ModifyTextMenuHandler(
    category: String,
    private val searchByAuthor: Boolean,
): ModifyMenuHandler(category, "M_TEXT"), BotCommand {

    override fun isValidPayload(payload: String): Boolean = payload in editComments

    override suspend fun handleButtonAction(
        message: Message,
        pressedBy: String,
        validPayload: String
    ) = when (validPayload) {
        customEditPayload -> when {
            searchByAuthor -> "ℹ\uFE0F Отредактируй оригинальное сообщение"
            else -> "\uD83D\uDE22 Пока не реализовано"
        }
        infoPayload -> {
            TelegramClient.sendChatMessage(message.chat.id.toString(), TgTextOutput(UserMessages.helpBugurtMessage), replyMessageId = message.id)
            null
        }
        else -> message.modifyPost(searchByAuthor, editActions.getValue(validPayload))
    }

    override suspend fun createHandlerKeyboard(message: Message, pressedBy: String) = sequence {
        editComments
            .map { (key, comment) -> InlineKeyboardButton(comment, callbackData(key)) }
            .forEach { yield(listOf(it)) }
        yield(listOf(retrieveMainMenuHandler().backButton))
    }.toList().let { InlineKeyboardMarkup(it) }

    override fun canHandle(context: MessageContext): Boolean = searchByAuthor && context.isEdit

    override suspend fun MessageContext.handle() {
        if (searchByAuthor && isEdit) {
            val suggestion = SuggestionStore.findLastByAuthorChatId(message.chat.id) ?: return
            if (suggestion.userCanEdit()) {
                val text = suggestion.modifyPost(searchByAuthor) { it.copy(postText = messageText) }
                if (text != null) {
                    TelegramClient.sendChatMessage(message.chat.id.toString(), TgTextOutput(text), replyMessageId = message.id)
                }
            } else {
                val newPostRemaining = with(suggestion.userNewPostSecondsRemaining()) { "${this/60}:${(this%60).toString().padStart(2, '0')}" }
                val text = "Изменять или удалять пост можно только в течении первых ${Setting.USER_EDIT_TIME_MINUTES.long()} минут, через $newPostRemaining можешь предложить еще один пост"
                TelegramClient.sendChatMessage(message.chat.id.toString(), TgTextOutput(text), replyMessageId = message.id)
            }
        }
    }

    companion object {
        private val lowercaseBugurtPartsRegex = "#[a-zа-я_]+|\\*.*?\\*|<.*?>".toRegex(RegexOption.IGNORE_CASE)
        private val malformedBugurtRegex = "(@\\n?)?([^@\\n]+\\n?(@\\s*\\n?)+)+[^@\\n]+(\\n?@)?".toRegex(RegexOption.MULTILINE)
        private val validBugurtRegex = """(^([^@a-zа-я\n][^a-zа-я\n]+|#[a-zа-я_]+|[*<][^\n]*?[*>]|)+${'$'})(\n@\s*?\n(^([^@a-zа-я\n][^a-zа-я\n]+|#[a-zа-я_]+|[*<][^\n]*?[*>]|)+${'$'}))*""".toRegex(RegexOption.MULTILINE)
        private const val infoPayload = "info"

        fun isValidBugurt(bugurt: String): Boolean = validBugurtRegex.matches(bugurt)

        private val editComments = mapOf(
            customEditPayload to "\uD83D\uDCDD Изменить текст вручную \uD83D\uDCDD",
            "upper" to "\uD83E\uDE84 Оформить автоматически \uD83E\uDE84",
            "prefix" to "\uD83D\uDDD1 Удалить вступление \uD83D\uDDD1",
            "postfix" to "\uD83D\uDDD1 Удалить послесловие \uD83D\uDDD1",
            infoPayload to "ℹ\uFE0F Правила оформления ℹ\uFE0F",
        )

        private val editActions = mapOf<String, (UserSuggestion) -> UserSuggestion>(
            "upper" to { it.copy(postText = it.postText.prettifyBugurt()) },
            "prefix" to { it.copy(postText = it.postText.removeBugurtSurrounding(prefix = true)) },
            "postfix" to { it.copy(postText = it.postText.removeBugurtSurrounding(prefix = false)) },
        )

        private fun String.prettifyBugurt(): String = this.replace(malformedBugurtRegex) { match -> match.value
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
            val match = malformedBugurtRegex.find(this)
            if (match != null) {
                return if (prefix) this.removeRange(0, match.range.first)
                else this.removeRange(match.range.last + 1, this.length)
            }
            return this
        }
    }
}