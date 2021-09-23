package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.sendLastDaySchedule

object LastDayScheduleCommand : BotCommand {
    override val command = "/last_day_schedule"

    override suspend fun MessageContext.handle(): Unit = with(bot) {
        sendLastDaySchedule()
    }

}
