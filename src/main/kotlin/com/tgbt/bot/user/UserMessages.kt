package com.tgbt.bot.user

import com.tgbt.misc.loadResourceAsString

object UserMessages {

    val helpMessage = loadResourceAsString("user/help.md")
    val helpBugurtMessage = loadResourceAsString("user/help.bugurt.md")
    val startMessage = loadResourceAsString("user/start.md")
    val unbanRequestMessage = loadResourceAsString("user/pleaseunban.md")

    const val postPromotedMessage = "✅ Твой пост '%s' пробился сквозь предложку! Беги проверять"
    const val bannedErrorMessage = "\uD83D\uDEAB Ты заблокирован за пост '_%s_'.\nПричина: _%s_\nРаз в %s дней можно отправить заявку на разбан через команду /pleaseunban"
}