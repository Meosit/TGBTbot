package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.settings.Setting
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.output.TgTextOutput

object VkScheduleErrorCommand : BotCommand {
    override val command = "/vk_schedule_error"

    override suspend fun MessageContext.handle() {
        when (val value = messageText.removePrefix(command).trim()) {
            "" -> TelegramClient.sendChatMessage(chatId, TgTextOutput("Argument expected"), message.id)
            else -> {
                val markdownText = if (value.toIntOrNull() != null) {
                    Setting.VK_SCHEDULE_ERROR_MINUTES.save(value)
                    "Now responsible editor will be notified after $value minutes of his missed slot"
                } else {
                    "Integer value expected, got '$value'"
                }
                TelegramClient.sendChatMessage(chatId, TgTextOutput(markdownText), message.id)
            }
        }
    }
}