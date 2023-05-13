package com.tgbt.bot

interface BotCommand {

    val command: String

    fun canHandle(context: MessageContext): Boolean = context.messageText.startsWith(command)

    suspend fun MessageContext.handle()

    suspend fun handleCommand(context: MessageContext) = context.handle()

}