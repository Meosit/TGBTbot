package com.tgbt.bot.editor.button

import com.tgbt.BotJson
import com.tgbt.bot.CallbackButtonHandler
import com.tgbt.bot.CallbackNotificationText
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

    protected suspend fun modifyPost(message: Message, action: (UserSuggestion) -> UserSuggestion): CallbackNotificationText {
        val suggestion = SuggestionStore.findByChatAndMessageId(message.chat.id, message.id, byAuthor = false)
        return if (suggestion?.editorChatId != null && suggestion.editorMessageId != null) {
            val updated = action(suggestion)
            val keyboardJson = BotJson.encodeToString(InlineKeyboardMarkup.serializer(), MainMenuHandler.ROOT_KEYBOARD)
            SuggestionStore.update(updated, byAuthor = false)

            // footer links should not be previewed.
            val post = TgPreparedPost(
                updated.postText, updated.imageId,
                suggestionReference = suggestion.authorReference(false)
            )

            if (message.photo != null) {
                when {
                    updated.imageId != suggestion.imageId -> {
                        val imageUrl = post.maybeImage ?: message.photo.first().fileId
                        TelegramClient.editChatMessagePhoto(message.chat.id.toString(), message.id, imageUrl)
                    }

                    updated.imageId == null -> {
                        TelegramClient.editChatMessagePhoto(message.chat.id.toString(), message.id,
                            noImagePlaceholder
                        )
                    }
                }
                val caption = post.withoutImage.trimToLength(
                    1024,
                    "...\n_(пост стал длиннее 1024 символов, но отобразится корректно после публикации)_"
                )
                TelegramClient.editChatMessageCaption(message.chat.id.toString(), message.id, caption, keyboardJson)
            } else {
                val disableLinkPreview = post.footerMarkdown.contains("https://")
                        && !post.text.contains("https://")
                        && !(post.maybeImage?.isImageUrl() ?: false)
                TelegramClient.editChatMessageText(
                    message.chat.id.toString(),
                    message.id,
                    post.withImage,
                    keyboardJson,
                    disableLinkPreview = disableLinkPreview
                )
            }
            return "✏️✅ Пост изменен ✏️✅"
        } else {
            FinishedMenuHandler.finish(message)
        }
    }

    companion object {
        private const val noImagePlaceholder = "https://cdn.segmentnext.com/wp-content/themes/segmentnext/images/no-image-available.jpg"
    }
}