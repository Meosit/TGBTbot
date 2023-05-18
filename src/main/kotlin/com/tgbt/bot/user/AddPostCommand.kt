package com.tgbt.bot.user

import com.tgbt.BotOwnerIds
import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.bot.user.button.UserSuggestionMenuHandler
import com.tgbt.post.TgPreparedPost
import com.tgbt.settings.Setting
import com.tgbt.suggestion.*
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.api.imageId
import com.tgbt.telegram.api.verboseUserName
import com.tgbt.telegram.output.TgTextOutput
import org.slf4j.LoggerFactory

object AddPostCommand: BotCommand {

    override fun canHandle(context: MessageContext): Boolean = with(context) {
        val suggestion = SuggestionStore.findLastByAuthorChatId(message.chat.id)
        !isEdit && (suggestion == null || suggestion.userCanAddNewPosts())
    }

    override suspend fun MessageContext.handle() {
        val suggestion = UserSuggestion(
            message.id,
            message.chat.id,
            message.verboseUserName,
            postText = messageText,
            imageId = message.imageId
        )
        val post = TgPreparedPost(
            suggestion.postText, suggestion.imageId, footerMarkdown = Setting.FOOTER_MD.str(),
            authorSign = suggestion.authorReference(anonymous = false)
        )

        val alert = when {
            !Setting.SUGGESTIONS_ENABLED.bool() && chatId !in BotOwnerIds ->
                "❗\uFE0F Предложка выключена на данный момент"
            suggestion.postText.length <= 10 ->
                "❗\uFE0F Пост должен содержать хотя бы 10 символов"
            message.photo != null && post.withoutImage.length > TgPreparedPost.MAX_IMAGE_POST_SIZE ->
                "❗\uFE0F Пост целиком будет длиннее ${TgPreparedPost.MAX_IMAGE_POST_SIZE} символов и не может быть отображен из-за картинки без ссылки"
            post.withImage.length >= TgPreparedPost.MAX_TEXT_POST_SIZE ->
                "❗\uFE0F Пост целиком будет длиннее ${TgPreparedPost.MAX_TEXT_POST_SIZE} символов и не может быть отображен, используй телеграф"
            else -> null
        }

        if (alert != null) {
            TelegramClient.sendChatMessage(message.chat.id.toString(), TgTextOutput(alert), replyMessageId = message.id)
            return
        }

        post.sendTo(chatId, UserSuggestionMenuHandler.rootKeyboard(suggestion))?.let {
            SuggestionStore.insert(suggestion.copy(authorMessageId = it.id, originallySentAsPhoto = it.photo != null))
            logger.info("NEW SUGGESTION from ${message.verboseUserName}: '${suggestion.postTextTeaser()}'")
        }
    }

    private val logger = LoggerFactory.getLogger("AddPostCommand")
}