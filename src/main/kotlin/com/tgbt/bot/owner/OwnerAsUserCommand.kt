package com.tgbt.bot.owner

import com.tgbt.BotOwnerIds
import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.settings.Setting
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.output.TgTextOutput

object OwnerAsUserCommand : BotCommand {
    override val command = "/owner_as_user"

    override suspend fun MessageContext.handle() {
        when (val value = messageText.removePrefix(command).trim()) {
            "" -> TelegramClient.sendChatMessage(chatId, TgTextOutput("Argument expected"), message.id)
            "true", "false" -> {
                if (chatId in BotOwnerIds) {
                    Setting.OWNER_AS_USER.save(value)
                    val markdownText = if (value == "true")
                        "Now chat is in user mode, every message now is treated like you're a regular user. To turn off, send `/owner_as_user false`"
                    else
                        "Chat returned to the owner mode, all owner commands available again"
                    BotOwnerIds.forEach { TelegramClient.sendChatMessage(it, TgTextOutput(markdownText), message.id) }
                } else {
                    TelegramClient.sendChatMessage(chatId, TgTextOutput("Congrats, you know more than a regular user!"), message.id)
                }
            }
            else -> {
                TelegramClient.sendChatMessage(chatId, TgTextOutput("Invalid argument '$value'"), message.id)
            }
        }
    }
}