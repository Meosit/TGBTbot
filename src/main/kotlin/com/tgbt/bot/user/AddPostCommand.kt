package com.tgbt.bot.user

import com.tgbt.bot.MessageContext
import com.tgbt.bot.user.UserMessages.duplicateErrorMessage
import com.tgbt.bot.user.UserMessages.emptyErrorMessage
import com.tgbt.bot.user.UserMessages.postSuggestedMessage
import com.tgbt.bot.user.UserMessages.sizeErrorMessage
import com.tgbt.settings.Setting
import com.tgbt.suggestion.UserSuggestion
import com.tgbt.suggestion.postTextTeaser
import com.tgbt.telegram.imageId
import com.tgbt.telegram.output.TgTextOutput
import com.tgbt.telegram.verboseUserName
import org.slf4j.LoggerFactory

object AddPostCommand: PostCommand() {

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
                    logger.info("User ${message.verboseUserName} tried to add duplicate post: '${suggestion.postTextTeaser()}'")
                } else {
                    if (bot.suggestionStore.insert(suggestion)) {
                        val editTimeMinutes = bot.settings.long(Setting.USER_EDIT_TIME_MINUTES)
                        val suggestionDelayMinutes = bot.settings.long(Setting.USER_SUGGESTION_DELAY_MINUTES)
                        bot.tgMessageSender.sendChatMessage(chatId, TgTextOutput(postSuggestedMessage.format(editTimeMinutes, suggestionDelayMinutes)))
                        logger.info("NEW SUGGESTION from ${message.verboseUserName}: '${suggestion.postTextTeaser()}'")
                    }
                }
            }
        }
    }

    private val logger = LoggerFactory.getLogger("AddPostCommand")
}