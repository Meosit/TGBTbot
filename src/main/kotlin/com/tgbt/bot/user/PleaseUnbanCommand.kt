package com.tgbt.bot.user

import com.tgbt.ban.BanStore
import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.bot.editor.button.UnbanMenuHandler
import com.tgbt.misc.escapeMarkdown
import com.tgbt.misc.trimToLength
import com.tgbt.settings.Setting
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.api.toJson
import com.tgbt.telegram.output.TgTextOutput
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue
import kotlin.math.roundToLong

object PleaseUnbanCommand: BotCommand {
    override val command: String = "/pleaseunban"

    override suspend fun MessageContext.handle() {
        val ban = BanStore.findByChatId(message.chat.id)
        val reason = messageText.removePrefix(command).trim()
        val daysSinceLastRequest: Double = ban
            ?.let { ChronoUnit.HOURS.between(it.lastUnbanRequestTime.toInstant(), Instant.now()).absoluteValue }
            ?.let { (it.toDouble() * 10.0 / 24.0).roundToLong() / 10.0 }
            ?: 0.0
        val coolDownDays = Setting.UNBAN_REQUEST_COOL_DOWN_DAYS.long()
        when {
            ban == null -> TelegramClient.sendChatMessage(chatId, TgTextOutput("Ты и не был в бане!"), message.id)
            daysSinceLastRequest <= coolDownDays -> TelegramClient.sendChatMessage(chatId, TgTextOutput("Запросы на разбан можно отправлять раз в $coolDownDays дней, ты уже ждешь $daysSinceLastRequest дней"), message.id)
            reason == "" -> TelegramClient.sendChatMessage(chatId, TgTextOutput("Укажи причину, почему ты думаешь, что тебя стоит разбанить `/pleaseunban <оправдание>`"), message.id)
            else -> {
                val targetChat = Setting.EDITOR_CHAT_ID.str()
                BanStore.updateLastUnbanRequest(ban.authorChatId, Timestamp.from(Instant.now()))
                val daysInBan = ChronoUnit.DAYS.between(ban.insertedTime.toInstant(), Instant.now()).absoluteValue
                TelegramClient.sendChatMessage(targetChat,
                    TgTextOutput(UserMessages.unbanRequestMessage.format(
                        ban.authorName,
                        ban.authorChatId.toString(),
                        daysInBan.toString(),
                        ban.postTeaser.escapeMarkdown(),
                        ban.reason.escapeMarkdown(),
                        reason.trimToLength(500).escapeMarkdown()
                    ), UnbanMenuHandler.rootKeyboard(ban.authorChatId).toJson())
                )
                TelegramClient.sendChatMessage(chatId, TgTextOutput("\uD83D\uDCE8 Заявка на разбан отправлена"), message.id)
            }
        }
    }
}