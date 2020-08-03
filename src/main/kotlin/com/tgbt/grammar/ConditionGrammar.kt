package com.tgbt.grammar

import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.Grammar
import com.github.h0tk3y.betterParse.grammar.parser
import com.github.h0tk3y.betterParse.lexer.literalToken
import com.github.h0tk3y.betterParse.lexer.regexToken
import com.github.h0tk3y.betterParse.parser.Parser

object ConditionGrammar : Grammar<Expr>() {
    private val not by literalToken("not")
    private val and by literalToken("and")
    private val or by literalToken("or")
    private val ws by regexToken("\\s+", ignore = true)
    private val lpar by literalToken("(")
    private val rpar by literalToken(")")

    private val conditionalOperator by regexToken("<=|>=|=>|=<|==[<>=]")
    private val statType by regexToken("l(ikes?)?|r(eposts?)?|c(omments?)?|v(iews?)?")
    private val statValue by regexToken("\\d+")

    private val condition by statType and conditionalOperator and statValue map { (t, o, v) ->
        val operator = when (o.text) {
            "==", "=" -> ConditionalOperator.EQUAL
            ">" -> ConditionalOperator.GREATER
            "<" -> ConditionalOperator.LOWER
            ">=", "=>" -> ConditionalOperator.GREATER_OR_EQUAL
            "<=", "=<" -> ConditionalOperator.LOWER_OR_EQUAL
            else -> throw IllegalArgumentException("Unreachable code $o")
        }
        val intValue = v.text.toInt()
        when (t.text) {
            "likes", "like", "l" -> Likes(operator, intValue)
            "reposts", "repost", "r" -> Reposts(operator, intValue)
            "comments", "comment", "c" -> Comments(operator, intValue)
            "views", "view", "v" -> Views(operator, intValue)
            else -> throw IllegalArgumentException("Unreachable code $t")
        }
    }

    private val term: Parser<Expr> by condition or
            (-not * parser(this::term) map { Not(it) }) or
            (-lpar * parser(this::rootParser) * -rpar)

    private val andChain by leftAssociative(term, and) { l, _, r -> And(l, r) }
    private val orChain by leftAssociative(term, or) { l, _, r -> Or(l, r) }

    override val rootParser by leftAssociative(andChain, or) { l, _, r -> Or(l, r) }
}