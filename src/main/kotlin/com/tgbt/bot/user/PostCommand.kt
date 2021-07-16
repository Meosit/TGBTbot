package com.tgbt.bot.user

import com.tgbt.bot.BotCommand

abstract class PostCommand: BotCommand {
    override val command: String
        get() = throw IllegalStateException("${this::class.simpleName} can be called only directly")
}