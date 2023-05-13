package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.settings.Setting
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.output.TgTextOutput

object VkIdCommand : BotCommand {
    override val command = "/vk_id "

    override suspend fun MessageContext.handle() {
        when (val value = messageText.removePrefix(command)) {
            "" -> TelegramClient.sendChatMessage(chatId, TgTextOutput("Argument expected"), message.id)
            else -> {
                val markdownText = if (value.toLongOrNull() != null) {
                    Setting.VK_COMMUNITY_ID.save(value)
                    "VK community ID now is $value, please ensure that it's correct"
                } else {
                    "Integer value expected, got '$value'"
                }
                TelegramClient.sendChatMessage(chatId, TgTextOutput(markdownText), message.id)
            }
        }
    }
}