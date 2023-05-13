package com.tgbt.bot.button.modify

import com.tgbt.BotJson
import com.tgbt.bot.button.CallbackButtonHandler
import com.tgbt.bot.button.CallbackNotificationText
import com.tgbt.bot.button.MainMenuHandler
import com.tgbt.misc.isImageUrl
import com.tgbt.misc.trimToLength
import com.tgbt.post.TgPreparedPost
import com.tgbt.suggestion.SuggestionStore
import com.tgbt.suggestion.UserSuggestion
import com.tgbt.suggestion.authorReference
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.api.InlineKeyboardMarkup
import com.tgbt.telegram.api.Message

abstract class ModifyMenuHandler(category: String, id: String): CallbackButtonHandler(category, id) {

    protected suspend fun modifyPost(message: Message, byAuthor: Boolean, action: (UserSuggestion) -> UserSuggestion): CallbackNotificationText {
        val suggestion = SuggestionStore.findByChatAndMessageId(message.chat.id, message.id, byAuthor)
        return if (suggestion != null && (byAuthor || suggestion.editorChatId != null && suggestion.editorMessageId != null)) {
            val updated = action(suggestion)
            val keyboardJson = BotJson.encodeToString(InlineKeyboardMarkup.serializer(), retrieveMainMenuHandler().rootKeyboard)
            SuggestionStore.update(updated, byAuthor)

            // footer links should not be previewed.
            val post = TgPreparedPost(
                updated.postText, updated.imageId,
                suggestionReference = suggestion.authorReference(anonymous = !byAuthor)
            )

            if (message.photo != null) {
                when {
                    updated.imageId == null -> {
                        TelegramClient.editChatMessagePhoto(message.chat.id.toString(), message.id,
                            noImagePlaceholder
                        )
                    }

                    updated.imageId != suggestion.imageId -> {
                        val imageUrl = post.maybeImage ?: message.photo.first().fileId
                        TelegramClient.editChatMessagePhoto(message.chat.id.toString(), message.id, imageUrl)
                    }
                }
                val caption = post.withoutImage.trimToLength(
                    1024,
                    "...\n_(пост длиннее 1024 символов, но отобразится корректно после публикации)_"
                )
                TelegramClient.editChatMessageCaption(message.chat.id.toString(), message.id, caption, keyboardJson)
            } else {
                val disableLinkPreview = post.footerMarkdown.contains("https://")
                        && !post.text.contains("https://")
                        && !(post.maybeImage?.isImageUrl() ?: false)

                val messageText = if (updated.imageId?.isImageUrl() == false)
                    post.copy(maybeImage = unlinkabeImagePlaceholder).withImage
                else post.withImage
                TelegramClient.editChatMessageText(
                    message.chat.id.toString(),
                    message.id,
                    messageText,
                    keyboardJson,
                    disableLinkPreview = disableLinkPreview
                )
            }
            return "✏️✅ Пост изменен ✏️✅"
        } else {
            retrieveMainMenuHandler().finishInteraction(message)
        }
    }

    abstract fun retrieveMainMenuHandler(): MainMenuHandler

    companion object {
        private const val noImagePlaceholder = "https://i.imgur.com/BTovKoW.jpg"
        private const val unlinkabeImagePlaceholder = "https://i.imgur.com/uvlOJR4.jpg"
    }
}