package com.tgbt.bot

interface BotCommand {

    val command: String get() = throw IllegalStateException("Command is not overridden but accessed")

    fun canHandle(context: MessageContext): Boolean = context.messageText.startsWith(command)

    suspend fun MessageContext.handle()

    suspend fun handleCommand(context: MessageContext) = context.handle()

}