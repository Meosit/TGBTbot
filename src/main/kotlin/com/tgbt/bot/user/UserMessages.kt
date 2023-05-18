package com.tgbt.bot.user

import com.tgbt.misc.loadResourceAsString

object UserMessages {

    val helpMessage = loadResourceAsString("user/help.md")
    val helpBugurtMessage = loadResourceAsString("user/help.bugurt.md")
    val startMessage = loadResourceAsString("user/start.md")

    val postPromotedMessage = "Твой пост '%s' пробился сквозь предложку! Беги проверять"
    val bannedErrorMessage = "Ты заблокирован за пост '%s'. Причина: _%s_"
}