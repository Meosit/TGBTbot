package com.tgbt.bot.editor

import com.tgbt.bot.BotContext
import com.tgbt.bot.MessageContext
import com.tgbt.bot.user.PostCommand
import com.tgbt.bot.user.UserMessages
import com.tgbt.misc.escapeMarkdown
import com.tgbt.misc.isImageUrl
import com.tgbt.misc.simpleFormatTime
import com.tgbt.misc.trimToLength
import com.tgbt.post.TgPreparedPost
import com.tgbt.settings.Setting
import com.tgbt.suggestion.UserSuggestion
import com.tgbt.suggestion.authorReference
import com.tgbt.suggestion.postTextTeaser
import com.tgbt.telegram.*
import com.tgbt.telegram.output.TgTextOutput
import org.slf4j.LoggerFactory
import java.time.Instant

class EditorUpdatePostCommand(private val suggestion: UserSuggestion): PostCommand() {

    override suspend fun MessageContext.handle(): Unit = with(bot) {
        if (replyMessage == null) {
            return
        }
        var updatedSuggestion: UserSuggestion? = null
        when {
            messageText.startsWith("/nopic") -> {
                updatedSuggestion = suggestion.copy(imageId = null)
                suggestionStore.update(updatedSuggestion, byAuthor = false)
                tgMessageSender.sendChatMessage(chatId, TgTextOutput(noPicMessage), replyMessageId = message.id)
                logger.info("Editor ${message.from?.simpleRef} remoted image for post '${suggestion.postTextTeaser()}' from ${suggestion.authorName}")
            }
            messageText.startsWith("/reject") -> {
                when (val value = messageText.removePrefix("/reject").trim()) {
                    "" -> tgMessageSender.sendChatMessage(chatId, TgTextOutput("Зачем использовать эту команду без комментария?"), message.id)
                    else -> {
                        if (suggestion.editorChatId != null && suggestion.editorMessageId != null) {
                            val actuallyDeleted = suggestionStore.removeByChatAndMessageId(suggestion.editorChatId, suggestion.editorMessageId, byAuthor = false)
                            if (actuallyDeleted) {
                                tgMessageSender.sendChatMessage(suggestion.authorChatId.toString(), TgTextOutput(UserMessages.postDiscardedWithCommentMessage
                                    .format(suggestion.postTextTeaser(), value.escapeMarkdown())))
                                val keyboardJson = json.stringify(InlineKeyboardMarkup.serializer(),
                                    InlineKeyboardButton("❌ Удалён ${message.from?.simpleRef ?: "anon"} c \uD83D\uDCAC в ${Instant.now().simpleFormatTime()} ❌", EditorButtonAction.DELETED_DATA).toMarkup())
                                tgMessageSender.editChatMessageKeyboard(suggestion.editorChatId.toString(), suggestion.editorMessageId, keyboardJson)
                                logger.info("Editor ${message.from?.simpleRef} rejected post '${suggestion.postTextTeaser()}' from ${suggestion.authorName} with comment '$value'")
                            }
                        }
                    }
                }
            }
            messageText.trim().isImageUrl() -> {
                updatedSuggestion = suggestion.copy(imageId = messageText.trim())
                suggestionStore.update(updatedSuggestion, byAuthor = false)
                tgMessageSender.sendChatMessage(chatId, TgTextOutput(addPicMessage), replyMessageId = message.id)
                logger.info("Editor ${message.from?.simpleRef} updated post image '${suggestion.postTextTeaser()}' from ${suggestion.authorName} with $messageText")
            }
        }
        if (updatedSuggestion != null) {
            val post = TgPreparedPost(updatedSuggestion.postText, updatedSuggestion.imageId,
                settings[Setting.FOOTER_MD], suggestion.authorReference(false))
            updateTelegramPost(replyMessage, post)
        }
    }

    private suspend fun BotContext.updateTelegramPost(
        message: Message,
        prepared: TgPreparedPost
    ) {
        val keyboardJson = message.replyMarkup?.let { json.stringify(InlineKeyboardMarkup.serializer(), it) }
        when {
            message.photo != null  -> {
                when {
                    prepared.maybeImage == null -> tgMessageSender
                        .editChatMessagePhoto(message.chat.id.toString(), message.id, noImagePlaceholder)
                    prepared.maybeImage != message.imageId -> tgMessageSender
                        .editChatMessagePhoto(message.chat.id.toString(), message.id, prepared.maybeImage)
                }
                tgMessageSender.editChatMessageCaption(message.chat.id.toString(), message.id, prepared.withoutImage.trimToLength(1024, "...\n_(пост стал длиннее чем 1024 символа)_"), keyboardJson)
            }
            else -> {
                // footer links should not be previewed.
                val disableLinkPreview = prepared.footerMarkdown.contains("https://")
                        && !prepared.text.contains("https://")
                        && !(prepared.maybeImage?.isImageUrl() ?: false)
                tgMessageSender.editChatMessageText(
                    message.chat.id.toString(),
                    message.id,
                    prepared.withImage,
                    keyboardJson,
                    disableLinkPreview = disableLinkPreview
                )
            }
        }
    }


    companion object {
        private val logger = LoggerFactory.getLogger("EditorUpdatePostCommand")
        private val noPicMessage = UserMessages.postPhotoDeletedMessage
        private val addPicMessage = UserMessages.photoUpdatedMessage
        private val noImagePlaceholder = "https://cdn.segmentnext.com/wp-content/themes/segmentnext/images/no-image-available.jpg"
    }
}