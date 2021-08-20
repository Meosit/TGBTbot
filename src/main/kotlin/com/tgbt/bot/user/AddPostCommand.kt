package com.tgbt.bot.user

import com.tgbt.bot.MessageContext
import com.tgbt.bot.user.UserMessages.duplicateErrorMessage
import com.tgbt.bot.user.UserMessages.emptyErrorMessage
import com.tgbt.bot.user.UserMessages.sizeErrorMessage
import com.tgbt.misc.loadResourceAsString
import com.tgbt.misc.trimToLength
import com.tgbt.settings.Setting
import com.tgbt.suggestion.UserSuggestion
import com.tgbt.telegram.imageId
import com.tgbt.telegram.output.TgTextOutput
import com.tgbt.telegram.verboseUserName
import org.slf4j.LoggerFactory

object AddPostCommand: PostCommand() {
    private val successMessage = loadResourceAsString("user/post.suggested.md")


    override suspend fun MessageContext.handle() {
        when (messageText.length) {
            in 0..9 -> bot.tgMessageSender
                .sendChatMessage(chatId, TgTextOutput(emptyErrorMessage))
            in 3501..Int.MAX_VALUE ->
                bot.tgMessageSender.sendChatMessage(chatId, TgTextOutput(sizeErrorMessage))
            else -> {
                val suggestion = UserSuggestion(
                    message.id,
                    message.chat.id,
                    message.verboseUserName,
                    postText = messageText,
                    imageId = message.imageId
                )
                if (bot.suggestionStore.findByAuthorAndPostText(message.chat.id, suggestion.postText) != null) {
                    bot.tgMessageSender.sendChatMessage(chatId, TgTextOutput(duplicateErrorMessage))
                    logger.info("User ${message.verboseUserName} tried to add duplicate post: '${suggestion.postText.trimToLength(20, "...")}'")
                } else {
                    if (bot.suggestionStore.insert(suggestion)) {
                        val editTimeMinutes = bot.settings[Setting.USER_EDIT_TIME_MINUTES].toLong()
                        val suggestionDelayMinutes = bot.settings[Setting.USER_SUGGESTION_DELAY_MINUTES].toLong()
                        bot.tgMessageSender.sendChatMessage(chatId, TgTextOutput(successMessage.format(editTimeMinutes, suggestionDelayMinutes)))
                        logger.info("NEW SUGGESTION from ${message.verboseUserName}: '${suggestion.postText.trimToLength(20, "...")}'")
                    }
                }
            }
        }
    }

    private val logger = LoggerFactory.getLogger("AddPostCommand")
}