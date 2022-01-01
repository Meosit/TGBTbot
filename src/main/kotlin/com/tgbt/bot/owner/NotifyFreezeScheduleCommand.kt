package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.settings.Setting
import com.tgbt.telegram.output.TgTextOutput

object NotifyFreezeScheduleCommand : BotCommand {
    override val command = "/notify_freeze_schedule "

    override suspend fun MessageContext.handle(): Unit = with(bot) {
        when (val value = messageText.removePrefix(command)) {
            "" -> tgMessageSender.sendChatMessage(chatId, TgTextOutput("Argument expected"), message.id)
            "true", "false" -> {
                settings[Setting.NOTIFY_FREEZE_SCHEDULE] = value
                val markdownText = if (value == "true")
                    "Enabled notification about freeze by SCHEDULE miss" else "Disabled notification about freeze by SCHEDULE miss"
                tgMessageSender.sendChatMessage(chatId, TgTextOutput(markdownText), message.id)
            }
            else -> {
                tgMessageSender.sendChatMessage(chatId, TgTextOutput("Invalid argument '$value'"), message.id)
            }
        }
    }
}