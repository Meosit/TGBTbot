package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.misc.escapeMarkdown
import com.tgbt.settings.Setting
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.output.TgTextOutput


object VkFreezeMentionsCommand : BotCommand {
    override val command = "/vk_freeze_mentions "

    override suspend fun MessageContext.handle() {
        val value = messageText.removePrefix(command).escapeMarkdown()
        Setting.VK_FREEZE_MENTIONS.save(value)
        val md = "Text '$value' would be added at the end of each VK freeze notification"
        TelegramClient.sendChatMessage(chatId, TgTextOutput(md), message.id)
    }
}