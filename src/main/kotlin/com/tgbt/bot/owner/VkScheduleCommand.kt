package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.misc.escapeMarkdown
import com.tgbt.misc.moscowZoneId
import com.tgbt.misc.simpleFormatTime
import com.tgbt.settings.Setting
import com.tgbt.settings.Settings
import com.tgbt.telegram.output.TgTextOutput
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter


data class VkScheduleSlot(val time: LocalTime, val user: String)

private class VkScheduleParseException(val index: Int, val row: String): RuntimeException("$index: \"$row\"")

object VkScheduleCommand : BotCommand {

    private val logger = LoggerFactory.getLogger("VkScheduleCommand")
    override val command = "/vk_schedule"
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

    private fun parseSchedule(raw: String): List<VkScheduleSlot> = raw.splitToSequence("\n")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapIndexed { i: Int, row: String -> scheduleItemRegex.matchEntire(row) ?: throw VkScheduleParseException(i, row) }
        .map { VkScheduleSlot(LocalTime.parse(it.groupValues[1].replace('.', ':'), timePattern), it.groupValues[2]) }
        .toList()


    fun findPastSlots(settings: Settings, coerceRange: LongRange): List<VkScheduleSlot> = try {
        val rawSchedule = settings[Setting.VK_SCHEDULE]
        val scheduleItems = parseSchedule(rawSchedule)
        val now = ZonedDateTime.now(moscowZoneId).toLocalTime()
        scheduleItems.filter { Duration.between(it.time, now).toMinutes() in coerceRange }
    } catch (e: VkScheduleParseException) {
        logger.error("Failed to parse already saved schedule! ${e.message}")
        emptyList()
    }

}