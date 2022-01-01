package com.tgbt.bot

import com.tgbt.bot.editor.EditorHelpCommand
import com.tgbt.bot.editor.EditorUpdatePostCommand
import com.tgbt.bot.editor.ForgottenPostsCommand
import com.tgbt.bot.owner.*
import com.tgbt.bot.user.*
import com.tgbt.misc.escapeMarkdown
import com.tgbt.settings.Setting.EDITOR_CHAT_ID
import com.tgbt.settings.Setting.SUGGESTIONS_ENABLED
import com.tgbt.telegram.Message
import com.tgbt.telegram.anyText
import com.tgbt.telegram.isPrivate
import com.tgbt.telegram.output.TgTextOutput
import io.ktor.client.features.*
import io.ktor.utils.io.*
import org.slf4j.LoggerFactory

data class MessageContext(
    val bot: BotContext,
    val chatId: String,
    val isEdit: Boolean,
    val messageText: String,
    val message: Message,
    val replyMessage: Message?
) {
    constructor(
        botContext: BotContext,
        message: Message,
        isEdit: Boolean
    ) :
            this(
                botContext,
                message.chat.id.toString(),
                isEdit,
                message.anyText ?: "",
                message,
                message.replyToMessage
            )

    suspend fun handleUpdate() {
        try {
            handleUpdateInternal()
        } catch (e: Exception) {
            val line = (e as? ClientRequestException)?.response?.content?.readUTF8Line()
            val message = "Unexpected error occurred while handling update, error message:\n`${e.message?.escapeMarkdown()}`" +
                        (line?.let { "\n\nResponse content:\n```${line.escapeMarkdown()}```" } ?: "")
            logger.error(message, e)
            if (line != null) {
                logger.error(line)
            }
            val output = TgTextOutput(message)
            bot.ownerIds.forEach { bot.tgMessageSender.sendChatMessage(it, output) }
        }
    }

    private suspend fun handleUpdateInternal() = when {
        chatId in bot.ownerIds -> {
            val command = OWNER_COMMANDS.find { it.canHandle(messageText) }
            command?.handleCommand(this) ?: bot.tgMessageSender
                .sendChatMessage(chatId, TgTextOutput("Unknown owner command"))
        }
        message.chat.isPrivate -> {
            val command = USER_COMMANDS.find { it.canHandle(messageText) }
            command?.handleCommand(this) ?: if (bot.settings.bool(SUGGESTIONS_ENABLED)) {
                val suggestion = bot.suggestionStore.findLastByAuthorChatId(message.chat.id)
                if (suggestion == null) {
                    AddPostCommand.handleCommand(this)
                } else {
                    UpdatePostCommand(suggestion).handleCommand(this)
                }
            } else {
                bot.tgMessageSender.sendChatMessage(chatId, TgTextOutput(UserMessages.suggestionsDisabledErrorMessage))
            }
        }
        bot.settings.str(EDITOR_CHAT_ID) == chatId -> {
            val command = EDITOR_COMMANDS.find { it.canHandle(messageText) }
            when {
                command != null -> command.handleCommand(this)
                replyMessage?.from?.isBot == true -> {
                    val suggestion = bot.suggestionStore.findByChatAndMessageId(message.chat.id, replyMessage.id, byAuthor = false)
                    if (suggestion != null) {
                        EditorUpdatePostCommand(suggestion).handleCommand(this)
                    }
                    Unit
                }
                else -> logger.info("Ignored editors chat message")
            }
        }
        else -> {
            bot.ownerIds.forEach { bot.tgMessageSender.sendChatMessage(it, TgTextOutput("Bot added in non-editors chat! Message dump:\n```$message```")) }
            bot.tgMessageSender.leaveGroup(chatId)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger("MessageContext")

        private val OWNER_COMMANDS: List<BotCommand> = listOf(
            ChannelCommand,
            CheckPeriodCommand,
            ConditionCommand,
            EditorsChatCommand,
            FooterCommand,
            ForceForwardCommand,
            ForcePoolingCommand,
            ForwardingCommand,
            OwnerHelpCommand,
            PhotoModeCommand,
            PostCountCommand,
            RetentionPeriodCommand,
            SendStatusCommand,
            SendSuggestionStatusCommand,
            SettingsCommand,
            SuggestionDelayCommand,
            SuggestionEditCommand,
            SuggestionPoolingCommand,
            SuggestionsCleanCommand,
            SuggestionsCommand,
            SuggestionsDeletionCommand,
            SuggestionsPromotionCommand,
            VkIdCommand,
            VkFreezeMentionsCommand,
            VkFreezeTimeoutCommand,
            VkScheduleCommand,
            VkScheduleErrorCommand,
            NotifyFreezeTimeoutCommand,
            NotifyFreezeScheduleCommand,
            SendFreezeStatusCommand,
            LastDayScheduleCommand,
            LastDayMissedCommand
        )

        private val USER_COMMANDS: List<BotCommand> = listOf(
            UserHelpCommand,
            UserStartCommand
        )

        private val EDITOR_COMMANDS: List<BotCommand> = listOf(
            EditorHelpCommand,
            ForgottenPostsCommand
        )
    }
}