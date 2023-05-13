package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.settings.Setting
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.output.TgTextOutput

object VkFreezeIgnoreCommand : BotCommand {
    override val command = "/vk_freeze_ignore "

    override suspend fun MessageContext.handle() {
        val value = messageText.removePrefix(command)
        when {
            value == "" -> TelegramClient.sendChatMessage(chatId, TgTextOutput("Argument expected"), message.id)
            value == "disable" -> {
                Setting.VK_FREEZE_IGNORE_START.save("")
                Setting.VK_FREEZE_IGNORE_END.save("")
                val markdownText ="Freeze ignore period was disabled"
                TelegramClient.sendChatMessage(chatId, TgTextOutput(markdownText), message.id)
            }
            !value.matches("([0-1][0-9]|2[0-3]):[0-5][0-9]-([0-1][0-9]|2[0-3]):[0-5][0-9]".toRegex()) ->
                TelegramClient.sendChatMessage(chatId, TgTextOutput("Argument in format `HH:MM-HH:MM` expected"), message.id)
            else -> {
                val (start, end) = value.split("-")
                Setting.VK_FREEZE_IGNORE_START.save(start)
                Setting.VK_FREEZE_IGNORE_END.save(end)
                val markdownText ="Now editors will NOT be notified from $start till $end if there will be no posts"
                TelegramClient.sendChatMessage(chatId, TgTextOutput(markdownText), message.id)
            }
        }
    }
}