package com.tgbt.bot.owner

import com.github.h0tk3y.betterParse.grammar.tryParseToEnd
import com.github.h0tk3y.betterParse.parser.ErrorResult
import com.github.h0tk3y.betterParse.parser.Parsed
import com.tgbt.bot.MessageContext
import com.tgbt.grammar.ConditionGrammar
import com.tgbt.grammar.Expr
import com.tgbt.settings.Setting
import com.tgbt.telegram.output.TgTextOutput

const val CONDITION_COMMAND = "/condition "

suspend fun MessageContext.handleConditionCommand() {
    when (val maybeExpr = ConditionGrammar.tryParseToEnd(message.removePrefix(CONDITION_COMMAND))) {
        is Parsed -> {
            val exprJson = json.stringify(Expr.serializer(), maybeExpr.value)
            settings[Setting.CONDITION_EXPR] = exprJson
            val markdownText = if (exprJson == settings[Setting.CONDITION_EXPR])
                "Condition updated successfully" else "Failed to save condition to database"
            tgMessageSender.sendChatMessage(chatId, TgTextOutput(markdownText), messageId)
        }
        is ErrorResult -> {
            tgMessageSender.sendChatMessage(chatId, TgTextOutput("Invalid condition syntax: $maybeExpr"), messageId)
        }
    }
}