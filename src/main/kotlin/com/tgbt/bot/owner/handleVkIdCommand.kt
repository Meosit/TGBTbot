package com.tgbt.bot.owner

import com.tgbt.bot.MessageContext
import com.tgbt.settings.Setting
import com.tgbt.telegram.output.TgTextOutput

const val VK_ID_COMMAND = "/vkid "

suspend fun MessageContext.handleVkIdCommand() {
    when (val value = message.removePrefix(VK_ID_COMMAND)) {
        "" -> tgMessageSender.sendChatMessage(chatId, TgTextOutput("Argument expected"), messageId)
        else -> {
            val markdownText = if (value.toLongOrNull() != null) {
                settings[Setting.VK_COMMUNITY_ID] = value
                "VK community ID now is $value, please ensure that it's correct"
            } else {
                "Integer value expected, got '$value'"
            }
            tgMessageSender.sendChatMessage(chatId, TgTextOutput(markdownText), messageId)
        }
    }
}