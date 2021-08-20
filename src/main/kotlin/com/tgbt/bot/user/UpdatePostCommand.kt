package com.tgbt.bot.user

import com.tgbt.bot.MessageContext
import com.tgbt.bot.user.UserMessages.emptyErrorMessage
import com.tgbt.bot.user.UserMessages.internalErrorMessage
import com.tgbt.bot.user.UserMessages.invalidPhotoErrorMessage
import com.tgbt.bot.user.UserMessages.invalidPhotoNoAttachmentErrorMessage
import com.tgbt.bot.user.UserMessages.photoUpdatedAttachmentMessage
import com.tgbt.bot.user.UserMessages.photoUpdatedMessage
import com.tgbt.bot.user.UserMessages.postDeletedMessage
import com.tgbt.bot.user.UserMessages.postPhotoDeletedMessage
import com.tgbt.bot.user.UserMessages.postUpdatedMessage
import com.tgbt.bot.user.UserMessages.updateTimeoutErrorMessage
import com.tgbt.misc.isImageUrl
import com.tgbt.misc.trimToLength
import com.tgbt.settings.Setting
import com.tgbt.suggestion.SuggestionStatus
import com.tgbt.suggestion.UserSuggestion
import com.tgbt.telegram.imageId
import com.tgbt.telegram.output.TgTextOutput
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit

class UpdatePostCommand(private val suggestion: UserSuggestion) : PostCommand() {

    override suspend fun MessageContext.handle(): Unit = with(bot) {
        val diffMinutes = ChronoUnit.MINUTES.between(suggestion.insertedTime.toInstant(), Instant.now())
        val editTimeMinutes = settings[Setting.USER_EDIT_TIME_MINUTES].toLong()
        val suggestionDelayMinutes = settings[Setting.USER_SUGGESTION_DELAY_MINUTES].toLong()
        logger.info("Updating post, minutes after insert: $diffMinutes")
        when {
            diffMinutes >= suggestionDelayMinutes -> {
                AddPostCommand.handleCommand(this@handle)
            }
            diffMinutes >= editTimeMinutes -> {
                val updatedPost = suggestion.copy(status = SuggestionStatus.READY_FOR_SUGGESTION)
                suggestionStore.update(updatedPost, byAuthor = true)
                tgMessageSender.sendChatMessage(chatId, TgTextOutput(updateTimeoutErrorMessage
                    .format(editTimeMinutes, suggestionDelayMinutes - diffMinutes)))
                logger.info("User ${suggestion.authorName} tried to update post after timeout '${updatedPost.postText.trimToLength(20, "...")}'")
            }
            isEdit && messageText.isBlank() ->
                tgMessageSender.sendChatMessage(chatId, TgTextOutput(emptyErrorMessage))
            isEdit && messageText.isNotBlank() -> {
                if (messageText.length in 10..3500) {
                    val updatedPost = suggestion.copy(postText = messageText, imageId = message.imageId)
                    suggestionStore.update(updatedPost, byAuthor = true)
                    tgMessageSender.sendChatMessage(chatId, TgTextOutput(postUpdatedMessage))
                    logger.info("User ${suggestion.authorName} updated post text to '${updatedPost.postText.trimToLength(20, "...")}'")
                } else {
                    tgMessageSender.sendChatMessage(chatId, TgTextOutput(emptyErrorMessage))
                }
            }
            !isEdit && messageText.startsWith("/delete") -> {
                suggestionStore.removeByChatAndMessageId(
                    suggestion.authorChatId,
                    suggestion.authorMessageId,
                    byAuthor = true
                )
                tgMessageSender.sendChatMessage(chatId, TgTextOutput(postDeletedMessage))
                logger.info("User ${suggestion.authorName} deleted post '${suggestion.postText.trimToLength(20, "...")}'")
            }
            !isEdit && messageText.startsWith("/nopic") -> {
                suggestionStore.update(suggestion.copy(imageId = null), byAuthor = true)
                tgMessageSender.sendChatMessage(chatId, TgTextOutput(postPhotoDeletedMessage))
                logger.info("User ${suggestion.authorName} removed pic for post: '${suggestion.postText.trimToLength(20, "...")}'")
            }
            !isEdit && message.imageId != null -> {
                if (suggestion.postText.length >= 3500) {
                    tgMessageSender.sendChatMessage(chatId, TgTextOutput(invalidPhotoNoAttachmentErrorMessage))
                    logger.info("User ${suggestion.authorName} tried to add photo by attachment while post is too long, post: '${suggestion.postText.trimToLength(20, "...")}'")
                } else {
                    val updatedPost = suggestion.copy(imageId = message.imageId)
                    suggestionStore.update(updatedPost, byAuthor = true)
                    tgMessageSender.sendChatMessage(chatId, TgTextOutput(photoUpdatedAttachmentMessage))
                    logger.info("User ${suggestion.authorName} updated a pic BY ATTACHMENT for post: '${suggestion.postText.trimToLength(20, "...")}'")
                }
            }
            !isEdit && messageText.trim().isImageUrl() -> {
                val updatedPost = suggestion.copy(imageId = messageText.trim())
                suggestionStore.update(updatedPost, byAuthor = true)
                tgMessageSender.sendChatMessage(chatId, TgTextOutput(photoUpdatedMessage))
                logger.info("User ${suggestion.authorName} updated a pic BY LINK for post: '${suggestion.postText.trimToLength(20, "...")}'")
            }
            !isEdit && (message.imageId == null || !messageText.trim().isImageUrl()) -> {
                tgMessageSender.sendChatMessage(chatId, TgTextOutput(invalidPhotoErrorMessage))
            }
            else -> {
                tgMessageSender.sendChatMessage(chatId, TgTextOutput(internalErrorMessage))
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger("UpdatePostCommand")
    }
}