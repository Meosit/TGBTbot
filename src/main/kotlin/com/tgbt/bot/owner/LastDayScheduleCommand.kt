package com.tgbt.bot.owner

import com.tgbt.BotOwnerIds
import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.misc.*
import com.tgbt.post.localTime
import com.tgbt.post.toPost
import com.tgbt.post.zonedTime
import com.tgbt.settings.Setting
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.output.TgTextOutput
import com.tgbt.vk.VkPost
import com.tgbt.vk.VkPostLoader
import java.time.Duration

object LastDayScheduleCommand : BotCommand {
    override val command = "/last_day_schedule"

    override suspend fun MessageContext.handle() {
        sendLastDaySchedule(onlyMissed = false)
    }

    suspend fun sendLastDaySchedule(onlyMissed: Boolean = false) {
        doNotThrow("Failed to send last 24 hours schedule to TG") {
            val communityId = Setting.VK_COMMUNITY_ID.long()
            val schedule = VkScheduleCommand.parseSchedule()
            val slotError = Setting.VK_SCHEDULE_ERROR_MINUTES.long()
            val now = zonedNow()

            val last24hoursPosts = VkPostLoader
                .load(50, communityId)
                .filter { it.isPinned + it.markedAsAds == 0 }
                .map(VkPost::toPost)
                .filterNot { it.text.contains("#БТnews") }
                .filter { Duration.between(it.zonedTime, now).toMinutes() < 24 * 60 }

            val merged = VkScheduleCommand.mergePostsWithSchedule(schedule, last24hoursPosts, slotError)

            val message = merged.filter { !onlyMissed || (it.first != null && it.second == null) }.joinToString(
                prefix = if (onlyMissed) "Пропущеные посты за последние 24 часа:\n" else "Посты за последние 24 часа (сначала старые):\n",
                separator = if (onlyMissed) "\n" else "\n\n"
            ) {
                val slot = it.first
                val post = it.second
                when {
                    post != null -> {
                        val stats = with(post.stats) { ("${likes}\uD83E\uDD0D ${reposts}\uD83D\uDCE2 ${comments}\uD83D\uDCAC ${views}\uD83D\uDC41") }
                        val ref = slot?.let { "\n> в слот от ${slot.user}" } ?: ""
                        "*${post.localTime.simpleFormatTime()}*\n> '${post.text.trimToLength(20, "…").replace('\n', ' ').escapeMarkdown()}' \n> $stats$ref"
                    }
                    slot != null -> "- ${slot.time.simpleFormatTime()}: *Слот пропущен ${slot.user}*"
                    else -> "- Эта строчка не должна здесь быть..."
                }
            }
            BotOwnerIds.forEach { TelegramClient.sendChatMessage(it, TgTextOutput(message.trim())) }
        }
    }

}
