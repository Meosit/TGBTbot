package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.sendLastDaySchedule

object LastDayMissedCommand : BotCommand {
    override val command = "/last_day_missed"

    override suspend fun MessageContext.handle(): Unit = with(bot) {
        sendLastDaySchedule(onlyMissed = true)
    }

}
