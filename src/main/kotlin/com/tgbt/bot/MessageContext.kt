package com.tgbt.bot

import com.tgbt.bot.owner.*
import com.tgbt.bot.user.*
import com.tgbt.misc.escapeMarkdown
import com.tgbt.settings.Setting.EDITOR_CHAT_ID
import com.tgbt.settings.Setting.SUGGESTIONS_ENABLED
import com.tgbt.telegram.Message
import com.tgbt.telegram.anyText
import com.tgbt.telegram.isPrivate
import com.tgbt.telegram.output.TgTextOutput
import com.tgbt.telegram.verboseUserName
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
                    (line?.let { "\n\nResponse content:\n`${line.escapeMarkdown()}`" } ?: "")
            logger.error(message, e)
            if (line != null) {
                logger.error(line)
            }
            val output = TgTextOutput(message)
            bot.ownerIds.forEach { bot.tgMessageSender.sendChatMessage(it, output) }
        }
    }

    suspend fun handleUpdateInternal() = when {
        chatId in bot.ownerIds -> {
            val command = OWNER_COMMANDS.find { it.canHandle(messageText) }
            command?.handleCommand(this) ?: bot.tgMessageSender
                .sendChatMessage(chatId, TgTextOutput("Unknown owner command"))
        }
        message.chat.isPrivate -> {
            if (bot.settings[SUGGESTIONS_ENABLED].toBoolean()) {
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
        bot.settings[EDITOR_CHAT_ID] == chatId && replyMessage != null -> {
            logger.warn("Editor branch for ${message.verboseUserName}")
        }
        else -> logger.warn("Unreachable update branch for ${message.verboseUserName}")
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
            SettingsCommand,
            SuggestionDelayCommand,
            SuggestionEditCommand,
            SuggestionPoolingCommand,
            SuggestionsCleanCommand,
            SuggestionsCommand,
            SuggestionsDeletionCommand,
            SuggestionsPromotionCommand,
            VkIdCommand
        )

        private val USER_COMMANDS: List<BotCommand> = listOf(
            UserHelpCommand,
            UserStartCommand
        )
    }
}