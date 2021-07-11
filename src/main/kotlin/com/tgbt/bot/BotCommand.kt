package com.tgbt.bot

interface BotCommand {

    val command: String

    fun canHandle(message: String): Boolean = message.startsWith(command)

    suspend fun MessageContext.handle()

    suspend fun handleCommand(context: MessageContext) = context.handle()

}