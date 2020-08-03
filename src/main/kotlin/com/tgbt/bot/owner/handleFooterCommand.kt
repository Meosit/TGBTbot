package com.tgbt.bot.owner

import com.tgbt.bot.MessageContext
import com.tgbt.settings.Setting
import com.tgbt.telegram.output.TgTextOutput


const val FOOTER_COMMAND = "/footer "

suspend fun MessageContext.handleFooterCommand() {
    val value = message.removePrefix(FOOTER_COMMAND)
    settings[Setting.FOOTER_MD] = value
    val md = "Footer '$value' would be added at the end of each message with one empty line delimiter"
    tgMessageSender.sendChatMessage(chatId, TgTextOutput(md), messageId)

}