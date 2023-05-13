package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext

object LastDayMissedCommand : BotCommand {
    override val command = "/last_day_missed"

    override suspend fun MessageContext.handle() {
        LastDayScheduleCommand.sendLastDaySchedule(onlyMissed = true)
    }

}
