package com.tgbt.bot.editor

import com.tgbt.bot.BotContext
import com.tgbt.bot.MessageContext
import com.tgbt.bot.user.PostCommand
import com.tgbt.bot.user.UserMessages
import com.tgbt.misc.isImageUrl
import com.tgbt.misc.loadResourceAsString
import com.tgbt.misc.trimToLength
import com.tgbt.post.TgPreparedPost
import com.tgbt.settings.Setting
import com.tgbt.suggestion.UserSuggestion
import com.tgbt.suggestion.authorReference
import com.tgbt.telegram.InlineKeyboardMarkup
import com.tgbt.telegram.Message
import com.tgbt.telegram.imageId
import com.tgbt.telegram.output.TgTextOutput

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
            }
            messageText.startsWith("/nocomment") -> {
                updatedSuggestion = suggestion.copy(editorComment = "")
                suggestionStore.update(updatedSuggestion, byAuthor = false)
                tgMessageSender.sendChatMessage(chatId, TgTextOutput(noCommentMessage), replyMessageId = message.id)
            }
            messageText.trim().isImageUrl() -> {
                updatedSuggestion = suggestion.copy(imageId = messageText.trim())
                suggestionStore.update(updatedSuggestion, byAuthor = false)
                tgMessageSender.sendChatMessage(chatId, TgTextOutput(addPicMessage), replyMessageId = message.id)
            }
            messageText.trim().isNotBlank() -> {
                val markdown = if (messageText.trim().length <= 140) {
                    updatedSuggestion = suggestion.copy(editorComment = messageText.trim())
                    suggestionStore.update(updatedSuggestion, byAuthor = false)
                    addCommentMessage
                } else {
                    invalidCommentMessage
                }
                tgMessageSender.sendChatMessage(chatId, TgTextOutput(markdown), replyMessageId = message.id)
            }
            else -> {
                tgMessageSender.sendChatMessage(chatId, TgTextOutput(invalidPayloadMessage), replyMessageId = message.id)
            }
        }
        if (updatedSuggestion != null) {
            val post = TgPreparedPost(updatedSuggestion.postText, updatedSuggestion.imageId,
                settings[Setting.FOOTER_MD], suggestion.authorReference(false),
                updatedSuggestion.editorComment)
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
        private val noPicMessage = UserMessages.postPhotoDeletedMessage
        private val noCommentMessage = loadResourceAsString("editor/comment.deleted.md")
        private val addCommentMessage = loadResourceAsString("editor/comment.updated.md")
        private val invalidCommentMessage = loadResourceAsString("editor/comment.invalid.md")
        private val invalidPayloadMessage = loadResourceAsString("editor/invalid.md")
        private val addPicMessage = UserMessages.photoUpdatedMessage
        private val noImagePlaceholder = "https://cdn.segmentnext.com/wp-content/themes/segmentnext/images/no-image-available.jpg"
    }
}