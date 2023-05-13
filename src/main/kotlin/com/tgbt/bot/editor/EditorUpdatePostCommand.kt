package com.tgbt.bot.editor

import com.tgbt.BotJson
import com.tgbt.ban.BanStore
import com.tgbt.ban.UserBan
import com.tgbt.bot.MessageContext
import com.tgbt.bot.user.PostCommand
import com.tgbt.bot.user.UserMessages
import com.tgbt.misc.escapeMarkdown
import com.tgbt.misc.isImageUrl
import com.tgbt.misc.simpleFormatTime
import com.tgbt.misc.trimToLength
import com.tgbt.post.TgPreparedPost
import com.tgbt.settings.Setting
import com.tgbt.suggestion.SuggestionStore
import com.tgbt.suggestion.UserSuggestion
import com.tgbt.suggestion.authorReference
import com.tgbt.suggestion.postTextTeaser
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.api.*
import com.tgbt.telegram.output.TgTextOutput
import org.slf4j.LoggerFactory
import java.time.Instant

class EditorUpdatePostCommand(private val suggestion: UserSuggestion): PostCommand() {

    override suspend fun MessageContext.handle() {
        if (replyMessage == null) {
            return
        }
        var updatedSuggestion: UserSuggestion? = null
        when {
            messageText.startsWith("/nopic") -> {
                updatedSuggestion = suggestion.copy(imageId = null)
                SuggestionStore.update(updatedSuggestion, byAuthor = false)
                TelegramClient.sendChatMessage(chatId, TgTextOutput(noPicMessage), replyMessageId = message.id)
                logger.info("Editor ${message.from?.simpleRef} removed image for post '${suggestion.postTextTeaser()}' from ${suggestion.authorName}")
            }
            messageText.startsWith("/reject") -> {
                when (val value = messageText.removePrefix("/reject").trim()) {
                    "" -> TelegramClient.sendChatMessage(chatId, TgTextOutput("Зачем использовать эту команду без комментария?"), message.id)
                    else -> {
                        if (suggestion.editorChatId != null && suggestion.editorMessageId != null) {
                            val actuallyDeleted = SuggestionStore.removeByChatAndMessageId(suggestion.editorChatId, suggestion.editorMessageId, byAuthor = false)
                            if (actuallyDeleted) {
                                TelegramClient.sendChatMessage(suggestion.authorChatId.toString(), TgTextOutput(UserMessages.postDiscardedWithCommentMessage
                                    .format(suggestion.postTextTeaser().escapeMarkdown(), value.escapeMarkdown())))
                                val keyboardJson = BotJson.encodeToString(
                                    InlineKeyboardMarkup.serializer(),
                                    InlineKeyboardButton("❌ Удалён ${message.from?.simpleRef ?: "anon"} в ${Instant.now().simpleFormatTime()} \uD83D\uDCAC $value ❌".trimToLength(512, "…"), EditorButtonAction.DELETED_DATA).toMarkup())
                                TelegramClient.editChatMessageKeyboard(suggestion.editorChatId.toString(), suggestion.editorMessageId, keyboardJson)
                                logger.info("Editor ${message.from?.simpleRef} rejected post '${suggestion.postTextTeaser()}' from ${suggestion.authorName} with comment '$value'")
                            }
                        }
                    }
                }
            }
            messageText.startsWith("/ban") -> {
                when (val comment = messageText.removePrefix("/ban").trim()) {
                    "" -> TelegramClient.sendChatMessage(chatId, TgTextOutput("Нужно указать причину блокировки"), message.id)
                    else -> {
                        if (suggestion.editorChatId != null && suggestion.editorMessageId != null) {
                            if (BanStore.findByChatId(suggestion.authorChatId) == null) {
                                val ban = UserBan(
                                    authorChatId = suggestion.authorChatId,
                                    authorName = suggestion.authorName,
                                    postTeaser = suggestion.postTextTeaser(),
                                    reason = comment,
                                    bannedBy = message.from?.simpleRef ?: "unknown"
                                )
                                BanStore.insert(ban)
                                logger.info("User ${ban.authorName} was banned by ${ban.bannedBy}")
                            }
                            val actuallyDeleted = SuggestionStore.removeByChatAndMessageId(suggestion.editorChatId, suggestion.editorMessageId, byAuthor = false)
                            if (actuallyDeleted) {
                                TelegramClient.sendChatMessage(suggestion.authorChatId.toString(), TgTextOutput(UserMessages.bannedErrorMessage
                                    .format(suggestion.postTextTeaser().escapeMarkdown(), comment.escapeMarkdown())))
                                val keyboardJson = BotJson.encodeToString(
                                    InlineKeyboardMarkup.serializer(),
                                    InlineKeyboardButton("\uD83D\uDEAB Забанен ${message.from?.simpleRef ?: "anon"} в ${Instant.now().simpleFormatTime()} \uD83D\uDCAC $comment ❌".trimToLength(512, "…"), EditorButtonAction.DELETED_DATA).toMarkup())
                                TelegramClient.editChatMessageKeyboard(suggestion.editorChatId.toString(), suggestion.editorMessageId, keyboardJson)
                                logger.info("Editor ${message.from?.simpleRef} banned a user ${suggestion.authorName} because of post '${suggestion.postTextTeaser()}', comment '$comment'")
                            }
                        }
                    }
                }
            }
            messageText.trim().isImageUrl() -> {
                updatedSuggestion = suggestion.copy(imageId = messageText.trim())
                SuggestionStore.update(updatedSuggestion, byAuthor = false)
                TelegramClient.sendChatMessage(chatId, TgTextOutput(addPicMessage), replyMessageId = message.id)
                logger.info("Editor ${message.from?.simpleRef} updated post image '${suggestion.postTextTeaser()}' from ${suggestion.authorName} with $messageText")
            }
        }
        if (updatedSuggestion != null) {
            val post = TgPreparedPost(updatedSuggestion.postText, updatedSuggestion.imageId,
                Setting.FOOTER_MD.str(), suggestion.authorReference(false))
            updateTelegramPost(replyMessage, post)
        }
    }

    private suspend fun updateTelegramPost(
        message: Message,
        prepared: TgPreparedPost
    ) {
        val keyboardJson = message.replyMarkup?.let { BotJson.encodeToString(InlineKeyboardMarkup.serializer(), it) }
        when {
            message.photo != null  -> {
                when {
                    prepared.maybeImage == null -> TelegramClient
                        .editChatMessagePhoto(message.chat.id.toString(), message.id, noImagePlaceholder)
                    prepared.maybeImage != message.imageId -> TelegramClient
                        .editChatMessagePhoto(message.chat.id.toString(), message.id, prepared.maybeImage)
                }
                TelegramClient.editChatMessageCaption(message.chat.id.toString(), message.id, prepared.withoutImage.trimToLength(1024, "...\n_(пост стал длиннее чем 1024 символа)_"), keyboardJson)
            }
            else -> {
                // footer links should not be previewed.
                val disableLinkPreview = prepared.footerMarkdown.contains("https://")
                        && !prepared.text.contains("https://")
                        && !(prepared.maybeImage?.isImageUrl() ?: false)
                TelegramClient.editChatMessageText(
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
