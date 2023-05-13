package com.tgbt.bot

import com.tgbt.BotOwnerIds
import com.tgbt.ban.BanStore
import com.tgbt.bot.editor.EditorHelpCommand
import com.tgbt.bot.editor.ForgottenSuggestionsCommand
import com.tgbt.bot.editor.UnbanCommand
import com.tgbt.bot.editor.button.BanMenuHandler
import com.tgbt.bot.editor.button.EditorModifyImageMenuHandler
import com.tgbt.bot.editor.button.RejectMenuHandler
import com.tgbt.bot.owner.*
import com.tgbt.bot.user.*
import com.tgbt.misc.escapeMarkdown
import com.tgbt.misc.trimToLength
import com.tgbt.settings.Setting.EDITOR_CHAT_ID
import com.tgbt.settings.Setting.SUGGESTIONS_ENABLED
import com.tgbt.suggestion.SuggestionStore
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.api.*
import com.tgbt.telegram.output.TgTextOutput
import io.ktor.client.plugins.*
import io.ktor.client.statement.*
import org.slf4j.LoggerFactory

data class MessageContext(
    val chatId: String,
    val isEdit: Boolean,
    val messageText: String,
    val message: Message,
    val replyMessage: Message?
) {
    constructor(
        message: Message,
        isEdit: Boolean
    ) :
            this(
                message.chat.id.toString(),
                isEdit,
                message.textWithFixedCommand(),
                message,
                message.replyToMessage
            )

    suspend fun handleUpdate() {
        try {
            logger.info("Message [${message.chat.title ?: ("${message.chat.firstName} ${message.chat.lastName}")};$chatId]" +
                    "(${message.from.simpleRef})" +
                    (if (isEdit) "{EDIT}" else "") +
                    (message.photo?.let { "{PH}" } ?: "") +
                    (if (message.photo == null && message.caption != null) "{FWD}" else  "") +
                    (replyMessage?.let { "{RE:${it.anyText?.trimToLength(20)}}" } ?: "") +
                    ": $messageText")
            handleUpdateInternal()
        } catch (e: Exception) {
            val line = (e as? ClientRequestException)?.response?.bodyAsText()
            val message = "Unexpected error occurred while handling update, error message:\n`${e.message?.escapeMarkdown()}`" +
                        (line?.let { "\n\nResponse content:\n```${line.escapeMarkdown()}```" } ?: "")
            logger.error(message, e)
            if (line != null) {
                logger.error(line)
            }
            val output = TgTextOutput(message)
            BotOwnerIds.forEach { TelegramClient.sendChatMessage(it, output) }
        }
    }

    private suspend fun handleUpdateInternal() = when {
        chatId in BotOwnerIds -> {
            val command = OWNER_COMMANDS.find { it.canHandle(messageText) }
            command?.handleCommand(this) ?: TelegramClient
                .sendChatMessage(chatId, TgTextOutput("Unknown owner command"))
        }
        message.chat.isPrivate -> {
            val command = USER_COMMANDS.find { it.canHandle(messageText) }
            val ban = BanStore.findByChatId(message.chat.id)
            if (ban == null) {
                command?.handleCommand(this) ?: if (SUGGESTIONS_ENABLED.bool()) {
                    val suggestion = SuggestionStore.findLastByAuthorChatId(message.chat.id)
                    if (suggestion == null) {
                        AddPostCommand.handleCommand(this)
                    } else {
                        UpdatePostCommand(suggestion).handleCommand(this)
                    }
                } else {
                    TelegramClient.sendChatMessage(chatId, TgTextOutput(UserMessages.suggestionsDisabledErrorMessage))
                }
            } else {
                TelegramClient.sendChatMessage(
                    chatId, TgTextOutput(UserMessages.bannedErrorMessage
                    .format(ban.postTeaser.escapeMarkdown(), ban.reason.escapeMarkdown())))
            }
        }
        EDITOR_CHAT_ID.str() == chatId -> {
            val command = EDITOR_COMMANDS.find { it.canHandle(messageText) }
            command?.handleCommand(this)
        }
        else -> {
            BotOwnerIds.forEach { TelegramClient.sendChatMessage(it, TgTextOutput("Bot added in non-editors chat! Message dump:\n```$message```")) }
            TelegramClient.leaveGroup(chatId)
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
            ForceVKForwardCommand,
            ForcePublishSuggestionsCommand,
            ForwardingCommand,
            OwnerHelpCommand,
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
            VkIdCommand,
            VkFreezeMentionsCommand,
            VkFreezeTimeoutCommand,
            VkFreezeIgnoreCommand,
            VkScheduleCommand,
            VkScheduleErrorCommand,
            NotifyFreezeTimeoutCommand,
            NotifyFreezeScheduleCommand,
            SendFreezeStatusCommand,
            LastDayScheduleCommand,
            LastDayMissedCommand,
            GatekeeperCommand,
        )

        private val USER_COMMANDS: List<BotCommand> = listOf(
            UserHelpCommand,
            UserStartCommand
        )

        private val EDITOR_COMMANDS: List<BotCommand> = listOf(
            EditorHelpCommand,
            ForgottenSuggestionsCommand,
            UnbanCommand,
            ForceVKForwardCommand,
            BanMenuHandler,
            RejectMenuHandler,
            EditorModifyImageMenuHandler
        )
    }
}