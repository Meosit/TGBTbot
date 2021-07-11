package com.tgbt.bot

import com.tgbt.bot.owner.*
import com.tgbt.telegram.Message
import com.tgbt.telegram.output.TgTextOutput
import org.slf4j.LoggerFactory

data class MessageContext(
    val bot: BotContext,
    val message: String,
    val chatId: String,
    val messageId: Long,
    val verboseUserName: String
) {
    constructor(
        botContext: BotContext,
        message: Message
    ) :
            this(
                botContext,
                message.text ?: "",
                message.chat.id.toString(),
                message.messageId,
                with(message.chat) {
                    username?.let { "@$it" } ?: "[$firstName${lastName?.let { " $it" } ?: ""}](tg://user?id=$id)"
                }
            )

    suspend fun handleCommand() {
        if (chatId in bot.ownerIds) {

            val command = OWNER_COMMANDS.find { it.canHandle(message) }
            command?.handleCommand(this) ?: bot.tgMessageSender
                .sendChatMessage(chatId, TgTextOutput("Unknown owner command"))
        } else {
            logger.info("Unknown user $verboseUserName tried to use this bot")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger("MessageContext")

        private val OWNER_COMMANDS: List<BotCommand> = listOf(
            ChannelCommand(),
            CheckPeriodCommand(),
            ConditionCommand(),
            FooterCommand(),
            ForceForwardCommand(),
            ForwardingCommand(),
            HelpCommand(),
            PhotoModeCommand(),
            PostCountCommand(),
            RetentionPeriodCommand(),
            SendStatusCommand(),
            SettingsCommand(),
            VkIdCommand()
        )
    }
}