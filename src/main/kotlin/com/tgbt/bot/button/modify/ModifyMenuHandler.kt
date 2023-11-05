package com.tgbt.bot.button.modify

import com.tgbt.bot.button.CallbackButtonHandler
import com.tgbt.bot.button.CallbackNotificationText
import com.tgbt.bot.button.SuggestionMenuHandler
import com.tgbt.misc.isImageUrl
import com.tgbt.misc.trimToLength
import com.tgbt.post.TgPreparedPost
import com.tgbt.post.TgPreparedPost.Companion.MAX_IMAGE_POST_SIZE
import com.tgbt.post.TgPreparedPost.Companion.MAX_TEXT_POST_SIZE
import com.tgbt.suggestion.SuggestionStore
import com.tgbt.suggestion.UserSuggestion
import com.tgbt.suggestion.authorReference
import com.tgbt.suggestion.userCanEdit
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.api.Message
import com.tgbt.telegram.api.toJson
import com.tgbt.telegram.output.TgTextOutput

abstract class ModifyMenuHandler(category: String, id: String): CallbackButtonHandler(category, id) {

    private data class ModifyMessageRef(
        val chatId: Long,
        val messageId: Long,
        val messageSentAsPhoto: Boolean,
    ) {
        constructor(message: Message): this(message.chat.id, message.id, messageSentAsPhoto = message.photo != null)

        constructor(userSuggestion: UserSuggestion, byAuthor: Boolean): this(
            if (byAuthor) userSuggestion.authorChatId else userSuggestion.editorChatId ?: throw IllegalStateException("Editor ID must be not null"),
            if (byAuthor) userSuggestion.authorMessageId else userSuggestion.editorMessageId ?: throw IllegalStateException("Editor ID must be not null") ,
            userSuggestion.originallySentAsPhoto
        )
    }

    protected suspend fun Message.modifyPost(byAuthor: Boolean, action: (UserSuggestion) -> UserSuggestion): CallbackNotificationText =
        modifyPost(ModifyMessageRef(this), byAuthor, null, action)

    protected suspend fun UserSuggestion.modifyPost(byAuthor: Boolean, action: (UserSuggestion) -> UserSuggestion): CallbackNotificationText {
        return modifyPost(ModifyMessageRef(this, byAuthor), byAuthor, this, action)
    }

    private suspend fun modifyPost(messageRef: ModifyMessageRef, byAuthor: Boolean, alreadyFound: UserSuggestion? = null, action: (UserSuggestion) -> UserSuggestion): CallbackNotificationText {
        val messageId = messageRef.messageId
        val chatId = messageRef.chatId.toString()

        val suggestion = alreadyFound
            ?: SuggestionStore.findByChatAndMessageId(messageRef.chatId, messageId, byAuthor)
            ?: return retrieveMainMenuHandler().renderFinishKeyboard(chatId, messageId)


        val updated = action(suggestion)
        val keyboardJson = retrieveMainMenuHandler().rootKeyboard(updated).toJson()

        if (byAuthor && !suggestion.userCanEdit()) {
            TelegramClient.editChatMessageKeyboard(chatId, messageId, keyboardJson)
            return "❗\uFE0F⏲ Время изменения истекло"
        }

        val post = TgPreparedPost(
            updated.postText, updated.imageId,
            editorComment = updated.editorComment,
            authorSign = suggestion.authorReference(anonymous = false)
        )

        val alert = when {
            updated.postText.length <= 10->
                "❗\uFE0F Изменения не сохранены: пост должен содержать хотя бы 10 символов"
            updated.imageId?.isImageUrl() == false && post.withoutImage.length > MAX_IMAGE_POST_SIZE && suggestion.imageId != updated.imageId ->
                "❗\uFE0F Изменения не сохранены: картинка файлом не может быть прикреплена к постам длиннее $MAX_IMAGE_POST_SIZE символов"
            updated.imageId?.isImageUrl() == false && post.withoutImage.length > MAX_IMAGE_POST_SIZE && suggestion.imageId == updated.imageId ->
                "❗\uFE0F Изменения не сохранены: сообщение стало длиннее $MAX_IMAGE_POST_SIZE символов и не может быть отображено из-за картинки без ссылки"
            updated.imageId?.isImageUrl() == true && post.withImage.length >= MAX_TEXT_POST_SIZE ->
                "❗\uFE0F Изменения не сохранены: сообщение стало длиннее $MAX_TEXT_POST_SIZE символов и не может быть отображено, используй телеграф"

            else -> null
        }
        if (alert != null) {
            TelegramClient.sendChatMessage(chatId, TgTextOutput(alert), replyMessageId = messageId)
            return null
        }

        SuggestionStore.update(updated, byAuthor)
        if (messageRef.messageSentAsPhoto) {
            when {
                post.maybeImage == null -> TelegramClient.editChatMessagePhoto(chatId, messageId, noImagePlaceholder)
                post.maybeImage != suggestion.imageId -> {
                    TelegramClient.editChatMessagePhoto(chatId, messageId, post.maybeImage)
                }
            }
            val caption = post.withoutImage.trimToLength(
                1024,
                "...\n_(пост длиннее 1024 символов, но отобразится корректно после публикации)_"
            )
            TelegramClient.editChatMessageCaption(chatId, messageId, caption, keyboardJson)
        } else {
            // footer links should not be previewed.
            val disableLinkPreview = post.footerMarkdown.contains("https://")
                    && !post.text.contains("https://")
                    && !(post.maybeImage?.isImageUrl() ?: false)

            val messageText = if (updated.imageId?.isImageUrl() == false)
                post.copy(maybeImage = unlinkabeImagePlaceholder).withImage
            else post.withImage
            TelegramClient.editChatMessageText(chatId, messageId, messageText, keyboardJson, disableLinkPreview)
        }
        return "✏️✅ Пост изменен"
    }

    abstract fun retrieveMainMenuHandler(): SuggestionMenuHandler

    companion object {
        @JvmStatic
        protected val customEditPayload = "custom"
        private const val noImagePlaceholder = "https://i.imgur.com/BTovKoW.jpg"
        private const val unlinkabeImagePlaceholder = "https://i.imgur.com/uvlOJR4.jpg"
    }
}