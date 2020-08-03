package com.tgbt.bot.owner

import com.tgbt.bot.MessageContext
import com.tgbt.settings.Setting
import com.tgbt.telegram.output.TgTextOutput


const val FORWARDING_COMMAND = "/forwarding "

suspend fun MessageContext.handleForwardingCommand() {
    when (val value = message.removePrefix(FORWARDING_COMMAND)) {
        "" -> tgMessageSender.sendChatMessage(chatId, TgTextOutput("Argument expected"), messageId)
        "true", "false" -> {
            settings[Setting.FORWARDING_ENABLED] = value
            val markdownText = if (value == "true")
                "VK -> TG Post forwarding enabled" else "VK -> TG Post forwarding disabled"
            tgMessageSender.sendChatMessage(chatId, TgTextOutput(markdownText), messageId)
        }
        else -> {
            tgMessageSender.sendChatMessage(chatId, TgTextOutput("Invalid argument '$value'"), messageId)
        }
    }
}