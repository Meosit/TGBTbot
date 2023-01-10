package com.tgbt.bot.owner

import com.github.h0tk3y.betterParse.grammar.tryParseToEnd
import com.github.h0tk3y.betterParse.parser.ErrorResult
import com.github.h0tk3y.betterParse.parser.Parsed
import com.tgbt.BotJson
import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.grammar.ConditionGrammar
import com.tgbt.grammar.Expr
import com.tgbt.settings.Setting
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.output.TgTextOutput


object ConditionCommand : BotCommand {
    override val command = "/condition "

    override suspend fun MessageContext.handle() {
        when (val maybeExpr = ConditionGrammar.tryParseToEnd(messageText.removePrefix(command))) {
            is Parsed -> {
                val exprJson = BotJson.encodeToString(Expr.serializer(), maybeExpr.value)
                Setting.CONDITION_EXPR.save(exprJson)
                val markdownText = if (exprJson == Setting.CONDITION_EXPR.str())
                    "Condition updated successfully" else "Failed to save condition to database"
                TelegramClient.sendChatMessage(chatId, TgTextOutput(markdownText), message.id)
            }
            is ErrorResult -> {
                TelegramClient.sendChatMessage(chatId, TgTextOutput("Invalid condition syntax: $maybeExpr"), message.id)
            }
        }
    }
}