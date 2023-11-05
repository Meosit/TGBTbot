package com.tgbt.bot.owner

import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.misc.escapeMarkdown
import com.tgbt.misc.simpleFormatTime
import com.tgbt.misc.zonedNow
import com.tgbt.post.Post
import com.tgbt.post.localTime
import com.tgbt.post.zonedTime
import com.tgbt.settings.Setting
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.output.TgTextOutput
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter


data class VkScheduleSlot(val time: LocalTime, val user: String)

private class VkScheduleParseException(val index: Int, val row: String) : RuntimeException("$index: \"$row\"")

object VkScheduleCommand : BotCommand {

    override val command = "/vk_schedule\n"
    private val scheduleItemRegex = """^(\d?\d[:.]\d\d)\s(.+)$""".toRegex()
    private val timePattern = DateTimeFormatter.ofPattern("H:mm")

    override suspend fun MessageContext.handle() {
        val value = messageText.removePrefix(command).trim()
        val md = try {
            val scheduleItems = parseSchedule(value)
            val sortedValue = scheduleItems
                .sortedBy { it.time }
                .joinToString(separator = "\n") { "${it.time.simpleFormatTime()} ${it.user}" }
            Setting.VK_SCHEDULE.save(sortedValue)
            "Schedule successfully set, parsed ${scheduleItems.size} time slots"
        } catch (e: VkScheduleParseException) {
            "Failed to parse schedule at row ${e.index}: `${e.row.escapeMarkdown()}`"
        }
        TelegramClient.sendChatMessage(chatId, TgTextOutput(md), message.id)
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

    fun parseSchedule() = parseSchedule(Setting.VK_SCHEDULE.str())

    fun mergePostsWithSchedule(
        involvedSlots: List<VkScheduleSlot>,
        lastPosts: List<Post>,
        slotError: Long
    ): List<Pair<VkScheduleSlot?, Post?>> {
        val now = zonedNow()
        val nowDate = zonedNow().toLocalDate()
        val mergedSlots = involvedSlots
            .map { slot ->
                slot to lastPosts.find { post ->
                    val time = if (now.toLocalTime() >= slot.time) slot.time.atDate(nowDate) else slot.time.atDate(
                        nowDate.minusDays(1)
                    )
                    Duration.between(time, post.zonedTime.toLocalDateTime()).abs().toMinutes() <= slotError
                }
            }
        val mergedPosts = lastPosts
            .map { post ->
                involvedSlots.find { slot ->
                    val time = if (now.toLocalTime() >= slot.time) slot.time.atDate(nowDate) else slot.time.atDate(
                        nowDate.minusDays(1)
                    )
                    Duration.between(time, post.zonedTime.toLocalDateTime()).abs().toMinutes() <= slotError
                } to post
            }

        val latestSchedule = (mergedPosts + mergedSlots).distinct().sortedBy {
            // sorting that all list occurred in past
            val time = it.second?.localTime ?: it.first?.time ?: LocalTime.MIN
            if (now.toLocalTime() >= time) time.atDate(nowDate) else time.atDate(nowDate.minusDays(1))
        }
        return latestSchedule
    }
}