package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.misc.escapeMarkdown
import com.tgbt.misc.simpleFormatTime
import com.tgbt.settings.Setting
import com.tgbt.settings.Settings
import com.tgbt.telegram.output.TgTextOutput
import java.time.LocalTime
import java.time.format.DateTimeFormatter


data class VkScheduleSlot(val time: LocalTime, val user: String)

private class VkScheduleParseException(val index: Int, val row: String) : RuntimeException("$index: \"$row\"")

object VkScheduleCommand : BotCommand {

    override val command = "/vk_schedule\n"
    private val scheduleItemRegex = """^(\d?\d[:.]\d\d)\s(.+)$""".toRegex()
    private val timePattern = DateTimeFormatter.ofPattern("H:mm")

    override suspend fun MessageContext.handle(): Unit = with(bot) {
        val value = messageText.removePrefix(command)
        val md = try {
            val scheduleItems = parseSchedule(value)
            val sortedValue = scheduleItems
                .sortedBy { it.time }
                .joinToString(separator = "\n") { "${it.time.simpleFormatTime()} ${it.user}" }
            settings[Setting.VK_SCHEDULE] = sortedValue
            "Schedule successfully set, parsed ${scheduleItems.size} time slots"
        } catch (e: VkScheduleParseException) {
            "Failed to parse schedule at row ${e.index}: `${e.row.escapeMarkdown()}`"
        }
        tgMessageSender.sendChatMessage(chatId, TgTextOutput(md), message.id)
    }

    private fun parseSchedule(raw: String): List<VkScheduleSlot> = raw
        .splitToSequence("\n")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { row: String -> scheduleItemRegex.matchEntire(row) }
        .map {
            if (it == null) null else VkScheduleSlot(
                LocalTime.parse(it.groupValues[1].replace('.', ':'), timePattern),
                it.groupValues[2]
            )
        }
        .filterNotNull()
        .toList()

    fun parseSchedule(settings: Settings) = parseSchedule(settings[Setting.VK_SCHEDULE])
}