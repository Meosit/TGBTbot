package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.settings.Setting
import com.tgbt.telegram.output.TgTextOutput

object VkFreezeTimeoutCommand : BotCommand {
    override val command = "/vk_freeze_timeout "

    override suspend fun MessageContext.handle(): Unit = with(bot) {
        when (val value = messageText.removePrefix(command)) {
            "" -> tgMessageSender.sendChatMessage(chatId, TgTextOutput("Argument expected"), message.id)
            else -> {
                val markdownText = if (value.toIntOrNull() != null) {
                    settings[Setting.VK_FREEZE_TIMEOUT_MINUTES] = value
                    "Now editors will be notified after $value minutes of VK freeze (no posts)"
                } else {
                    "Integer value expected, got '$value'"
                }
                tgMessageSender.sendChatMessage(chatId, TgTextOutput(markdownText), message.id)
            }
        }
    }
}