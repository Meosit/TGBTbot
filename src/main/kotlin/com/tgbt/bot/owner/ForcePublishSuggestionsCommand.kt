package com.tgbt.bot.owner

import com.tgbt.BotOwnerIds
import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.bot.editor.ForgottenSuggestionsCommand
import com.tgbt.bot.editor.button.EditorMainMenuHandler
import com.tgbt.bot.user.UserMessages
import com.tgbt.misc.doNotThrow
import com.tgbt.misc.escapeMarkdown
import com.tgbt.post.TgPreparedPost
import com.tgbt.settings.Setting
import com.tgbt.suggestion.SuggestionStatus
import com.tgbt.suggestion.SuggestionStore
import com.tgbt.suggestion.authorReference
import com.tgbt.suggestion.postTextTeaser
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.output.TgTextOutput
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit

object ForcePublishSuggestionsCommand : BotCommand {
    override val command = "/force_publish_suggestions"
    private val logger = LoggerFactory.getLogger(ForcePublishSuggestionsCommand::class.simpleName)

    override suspend fun MessageContext.handle() {
        forwardSuggestions(forcedByOwner = true)
        TelegramClient.sendChatMessage(chatId, TgTextOutput("Suggestions pooling finished"))
    }

    suspend fun forwardSuggestions(forcedByOwner: Boolean = false) {
        val targetChat = Setting.EDITOR_CHAT_ID.str()
        val suggestionsEnabled = Setting.SUGGESTIONS_ENABLED.bool()
        val footerMd = Setting.FOOTER_MD.str()
        if (suggestionsEnabled) {
            logger.info("Checking for posts which are ready for suggestion")
            doNotThrow("Failed to change suggestions status PENDING_USER_EDIT -> READY_FOR_SUGGESTION") {
                val suggestions = SuggestionStore.findByStatus(SuggestionStatus.PENDING_USER_EDIT)
                val editTimeMinutes = Setting.USER_EDIT_TIME_MINUTES.long()
                for (suggestion in suggestions) {
                    val diffMinutes = ChronoUnit.MINUTES.between(suggestion.insertedTime.toInstant(), Instant.now())
                    if (diffMinutes >= editTimeMinutes) {
                        SuggestionStore.update(
                            suggestion.copy(status = SuggestionStatus.READY_FOR_SUGGESTION),
                            byAuthor = true
                        )
                    }
                }
            }
            logger.info("Checking for new suggested posts")
            var forwarded = 0
            val suggestions = doNotThrow("Failed to fetch READY_FOR_SUGGESTION suggestions from DB") {
                SuggestionStore.findByStatus(SuggestionStatus.READY_FOR_SUGGESTION)
            }
            suggestions?.forEach { suggestion ->
                doNotThrow("Failed to send single suggestion to editors group") {
                    val post = TgPreparedPost(
                        suggestion.postText, suggestion.imageId, footerMarkdown = footerMd,
                        suggestionReference = suggestion.authorReference(false)
                    )
                    val editorMessage = post.sendTo(targetChat, EditorMainMenuHandler.rootKeyboard)
                    if (editorMessage != null) {
                        SuggestionStore.update(
                            suggestion.copy(
                                editorChatId = editorMessage.chat.id,
                                editorMessageId = editorMessage.id,
                                status = SuggestionStatus.PENDING_EDITOR_REVIEW
                            ),
                            byAuthor = true
                        )
                    }
                    forwarded++
                }
            }
            val forgotten = ForgottenSuggestionsCommand.notifyAboutForgottenSuggestions(forcedByOwner)
            val scheduled = postScheduledSuggestions(footerMd)
            if (forcedByOwner || forwarded > 0 || forgotten > 0 || scheduled > 0) {
                val message = "*SUGGESTIONS*\n" +
                        "\nRight now forwarded $forwarded suggestions from users." +
                        "\nEditors forgot to review $forgotten posts." +
                        "\nPosted $scheduled scheduled posts."
                logger.info(message)
                if (Setting.SEND_SUGGESTION_STATUS.bool()) {
                    BotOwnerIds.forEach { TelegramClient.sendChatMessage(it, TgTextOutput(message)) }
                }
            }
        } else {
            logger.info("Suggestions disabled, skipping...")
        }
    }

    private suspend fun postScheduledSuggestions(footerMd: String): Int {
        val targetChannel = Setting.TARGET_CHANNEL.str()
        var scheduled = 0
        val suggestions = doNotThrow("Failed to fetch scheduled suggestions from DB") {
            SuggestionStore.findReadyForSchedule()
        }
        logger.info("Currently there are ${suggestions?.size} ready for schedule posts, notifying...")
        suggestions?.forEach { suggestion ->
            doNotThrow("Failed to post scheduled suggestion to channel") {
                val anonymous = suggestion.status != SuggestionStatus.SCHEDULE_PUBLICLY
                val post = TgPreparedPost(
                    suggestion.postText, suggestion.imageId, footerMarkdown = footerMd,
                    suggestionReference = suggestion
                        .authorReference(anonymous)
                )
                post.sendTo(targetChannel)
                SuggestionStore.removeByChatAndMessageId(
                    suggestion.authorChatId,
                    suggestion.authorMessageId,
                    byAuthor = true
                )
                scheduled++
                TelegramClient.sendChatMessage(
                    suggestion.authorChatId.toString(),
                    TgTextOutput(
                        UserMessages.postPromotedMessage.format(
                            suggestion.postTextTeaser().escapeMarkdown()
                        )
                    )
                )
                logger.info("Posted scheduled post '${suggestion.postTextTeaser()}' from ${suggestion.authorName}")
            }
        }
        return scheduled
    }

}
