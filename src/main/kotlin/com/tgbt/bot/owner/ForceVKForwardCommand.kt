package com.tgbt.bot.owner

import com.tgbt.BotJson
import com.tgbt.BotOwnerIds
import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.grammar.Expr
import com.tgbt.misc.*
import com.tgbt.post.*
import com.tgbt.settings.Setting
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.output.TgTextOutput
import com.tgbt.vk.VkPost
import com.tgbt.vk.VkPostLoader
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import kotlin.math.min

object ForceVKForwardCommand : BotCommand {
    override val command = "/force_forward"

    private val logger = LoggerFactory.getLogger(ForceVKForwardCommand::class.simpleName)

    private val slotMissingMessageFormats = listOf(
        "*Очередное унижение от железки получает {user} за пропуск поста на {slotTime}, с последнего поста ({lastPostTime}) прошло {freeze} минут*",
        "*Внимание внимание, {user} проебался. Стоило бы поставить пост в {slotTime}, с последнего поста ({lastPostTime}) прошло {freeze} минут*",
        "*На мясных никакой надежды, {user} не поставил пост на {slotTime}. С последнего поста ({lastPostTime}) прошло {freeze} минут*",
        "*{user}, ну сколько можно тебе напоминать? Пост на {slotTime}. С последнего поста ({lastPostTime}) прошло {freeze} минут*",
        "*{user}, мне самому что-ли предложку разгребать? {slotTime} пропустил. С последнего поста ({lastPostTime}) прошло {freeze} минут*",
        "*Коллектив KFC 'У Бугурт-Палыча' осуждает {user} за пропуск поста на {slotTime}. С последнего поста ({lastPostTime}) прошло {freeze} минут*",
        "*Никогда такого не было и вот опять... {user} пропустил пост на {slotTime}. С последнего поста ({lastPostTime}) прошло {freeze} минут*",
        "*{user} проебался. Надо было пост поставить на {slotTime}. С последнего поста ({lastPostTime}) прошло {freeze} минут*",
        "*Чел ты {user}... Нет поста на {slotTime}. С последнего поста ({lastPostTime}) прошло {freeze} минут*",
        "*Давай по новой, {user}, всё хуйня. Нет поста на {slotTime}. С последнего поста ({lastPostTime}) прошло {freeze} минут*",
        "*Я бы может меньше спамил, но {user} опять забыл как кнопки нажимать. Нет поста на {slotTime}. С последнего поста ({lastPostTime}) прошло {freeze} минут*",
        "*{user}, ничему ты не учишься, ну сколько можно... {slotTime}. Опять. С последнего поста ({lastPostTime}) прошло {freeze} минут*",
        "*{user} настолько уверенный пользователь ПК, что опять забыл поставить пост на {slotTime} ставить. С последнего поста ({lastPostTime}) прошло {freeze} минут*",
        "*Я официально осуждаю {user} за проёб слота {slotTime}. С последнего поста ({lastPostTime}) прошло {freeze} минут*",
        "*Если бы не проёб {user} в {slotTime}, сидел бы себе молча посты из ВК перекидывал. С последнего поста ({lastPostTime}) прошло {freeze} минут*",
        "*Последнее китайское предупреждение {user}: не пропускай слоты на {slotTime}. С последнего поста ({lastPostTime}) прошло {freeze} минут*",
        "*Минут 10-15 минут 5-10 пятого 4-5 10 пятого, или как {user} пост на {slotTime} ставил. С последнего поста ({lastPostTime}) прошло {freeze} минут*",
        "*Очередной кожаный мешок {user} меня разочаровал пропустив слот в {slotTime}. С последнего поста ({lastPostTime}) прошло {freeze} минут*",
        "*Ну и мразь же ты, {user}, отвратительно. Нет поста в {slotTime}. С последнего поста ({lastPostTime}) прошло {freeze} минут*",
        "*{user}, поздравляю, ты только что пост. Что пост? Только что, в {slotTime}. С последнего поста ({lastPostTime}) прошло {freeze} минут*",
        "*Можно бесконечно смотреть на 3 вещи. Как горит огонь, как течёт вода и как {user} не ставит пост на {slotTime}. С последнего поста ({lastPostTime}) прошло {freeze} минут*"
    )

    override suspend fun MessageContext.handle() {
        forwardVkPosts(forcedByOwner = true)
        TelegramClient.sendChatMessage(chatId, TgTextOutput("Forward check finished"))
    }

    suspend fun forwardVkPosts(forcedByOwner: Boolean = false) {
        val enabled = Setting.FORWARDING_ENABLED.bool()
        if (enabled) {
            logger.info("Checking for new posts")
            val postsCount = Setting.POST_COUNT_TO_LOAD.int()
            val condition = BotJson.decodeFromString(Expr.serializer(), Setting.CONDITION_EXPR.str())
            val communityId = Setting.VK_COMMUNITY_ID.long()
            val footerMd = Setting.FOOTER_MD.str()
            val sendStatus = Setting.SEND_STATUS.bool()
            val targetChannel = Setting.TARGET_CHANNEL.str()
            val editorsChatId = Setting.EDITOR_CHAT_ID.str()

            val stats = mutableMapOf<String, Int>()
            val lastPosts: MutableList<Post> = mutableListOf()
            val postsToForward = doNotThrow("Failed to load or parse VK posts") {
                VkPostLoader
                    .load(postsCount, communityId)
                    .filter { it.isPinned + it.markedAsAds == 0 }
                    .map(VkPost::toPost)
                    .also { posts ->
                        stats["total"] = posts.size
                        logger.info("Loaded ${posts.size} posts in total")
                        val now = System.currentTimeMillis() / 1000
                        // expect them to be already sorted by time descending
                        posts
                            .take(5)
                            .filterNot { it.text.contains("#БТnews") }
                            .take(3)
                            .forEach { post -> lastPosts.add(post) }
                        stats["freeze"] = TimeUnit.SECONDS.toMinutes(now - (lastPosts.firstOrNull()?.unixTime ?: now)).toInt()
                    }
                    .filter { condition.evaluate(it.stats) }
                    .also {
                        stats["condition"] = it.size
                        logger.info("${it.size} posts left after filtering by forward condition")
                    }
                    .filterNot { PostStore.isPostedToTG(it) }
                    .sortedBy { it.unixTime }
                    .also {
                        stats["already"] = it.size
                        logger.info("${it.size} after checking for already forwarded posts")
                    }
                    .take(20)
                    .also { logger.info("Taking only first ${it.size} to avoid request rate errors") }
            }
            val forwarded = mutableListOf<String>()
            postsToForward?.forEach {
                doNotThrow("Post https://vk.com/wall${communityId}\\_${it.id} passes the criteria but failed to send\nTo have it in telegram, post manually.\nAlso") {
                    val prepared = TgPreparedPost(it.text, it.imageUrl, footerMd)
                    if (PostStore.insert(it)) {
                        logger.info(
                            "Inserted new post https://vk.com/wall${communityId}_${it.id} '${it.text.teaserString()}'"
                        )
                        prepared.sendTo(targetChannel)
                        val link = "https://vk.com/wall${communityId}_${it.id}"
                        val vk = it.stats
                        forwarded.add("[Post | ${vk.likes}\uD83E\uDD0D ${vk.reposts}\uD83D\uDCE2 ${vk.comments}\uD83D\uDCAC ${vk.views}\uD83D\uDC41]($link)")
                    }
                }
            }
            if (forcedByOwner || (sendStatus && forwarded.size > 0)) {
                doNotThrow("Failed to send stats to TG") {
                    val message = "*FORWARDING*\n" +
                            "\nRight now forwarded ${forwarded.size} posts from VK to Telegram:\n" +
                            "${stats["freeze"]} minutes since last VK post\n" +
                            "${stats["total"]} loaded in total\n" +
                            "${stats["condition"]} after filtering by condition\n" +
                            "Posts:\n> " + forwarded.joinToString("\n> ")
                    logger.info(message)
                    BotOwnerIds.forEach {
                        TelegramClient.sendChatMessage(
                            it,
                            TgTextOutput(message),
                            disableLinkPreview = true
                        )
                    }
                }
            }
            doNotThrow("Failed to send freeze notification") {
                if (lastPosts.isEmpty()) {
                    return@doNotThrow
                }

                val vkFreezeIgnoreStart = if (Setting.VK_FREEZE_IGNORE_START.str().isNotBlank()) LocalTime.parse(Setting.VK_FREEZE_IGNORE_START.str()) else null
                val vkFreezeIgnoreEnd = if (Setting.VK_FREEZE_IGNORE_END.str().isNotBlank()) LocalTime.parse(Setting.VK_FREEZE_IGNORE_END.str()) else null
                val vkFreezeTimeout = Setting.VK_FREEZE_TIMEOUT_MINUTES.int()
                val vkFreezeMentions = Setting.VK_FREEZE_MENTIONS.str()
                val vkFreezeNotifyTimeout = Setting.NOTIFY_FREEZE_TIMEOUT.bool()
                val vkFreezeNotifySchedule = Setting.NOTIFY_FREEZE_SCHEDULE.bool()
                val vkFreezeSendStatus = Setting.SEND_FREEZE_STATUS.bool()
                val slotError = Setting.VK_SCHEDULE_ERROR_MINUTES.long()

                if (vkFreezeIgnoreStart != null && vkFreezeIgnoreEnd != null) {
                    if (zonedNow().toLocalTime().inLocalRange(vkFreezeIgnoreStart, vkFreezeIgnoreEnd)) {
                        logger.info("Ignoring freeze notifications check due to ignore period enabled (${zonedNow().toLocalTime()} in $vkFreezeIgnoreStart..$vkFreezeIgnoreEnd")
                        return@doNotThrow
                    }
                }

                val oldestPostTime = lastPosts.first().localTime
                val freeze = when {
                    vkFreezeIgnoreEnd.inLocalRange(oldestPostTime, zonedNow().toLocalTime()) ->
                        Duration.between(vkFreezeIgnoreEnd, zonedNow().toLocalTime()).toMinutes().toInt()
                    else -> stats["freeze"] ?: 0
                }

                val latestSlotTime = zonedNow().minusMinutes(min(freeze.toLong() - 1, slotError)).toLocalTime()
                val schedule = VkScheduleCommand.parseSchedule()
                val involvedSlots = schedule.filter { slot -> slot.time.inLocalRange(oldestPostTime, latestSlotTime) }
                val latestSchedule = VkScheduleCommand.mergePostsWithSchedule(involvedSlots, lastPosts, slotError)
                val checkPeriod = Setting.CHECK_PERIOD_MINUTES.int()

                if (latestSchedule.last().second == null && freeze < vkFreezeTimeout) {
                    val slot = latestSchedule.last().first
                    val post = latestSchedule.findLast { it.second != null }?.second
                    if (vkFreezeNotifySchedule && slot != null && post != null) {
                        logger.info("Found missing slot for ${slot.user} with $freeze min freeze, slot error is $slotError min")
                        val message = generateSlotMissingMessage(
                            slot.time.simpleFormatTime(),
                            slot.user,
                            post.localTime.simpleFormatTime(),
                            freeze
                        )
                        TelegramClient.sendChatMessage(editorsChatId, TgTextOutput(message), disableLinkPreview = true)
                        if (vkFreezeSendStatus) BotOwnerIds.forEach {
                            TelegramClient.sendChatMessage(
                                it,
                                TgTextOutput(message),
                                disableLinkPreview = true
                            )
                        }
                    }
                }

                if (freeze in vkFreezeTimeout..(vkFreezeTimeout + checkPeriod * 5)) {
                    logger.info("More than $freeze minutes since last VK post, alerting...")
                    val message = latestSchedule.joinToString(
                        prefix = "*Уже $freeze минут ни одного нового поста ВК*. Последние посты по МСК:\n",
                        separator = "\n",
                        postfix = "\n$vkFreezeMentions"
                    ) {
                        val slot = it.first
                        val post = it.second
                        when {
                            post != null -> "- ${post.localTime.simpleFormatTime()}: '${post.text.teaserString().escapeMarkdown()}'"
                            slot != null -> "- ${slot.time.simpleFormatTime()}: *Слот пропущен ${slot.user}*"
                            else -> "- Эта строчка не должна здесь быть..."
                        }
                    }
                    if (vkFreezeNotifyTimeout) TelegramClient.sendChatMessage(editorsChatId, TgTextOutput(message), disableLinkPreview = true)
                    if (vkFreezeSendStatus) BotOwnerIds.forEach { TelegramClient.sendChatMessage(it, TgTextOutput(message), disableLinkPreview = true) }
                }

            }

            doNotThrow("Failed to clean up old posts from TG") {
                val retentionDays = Setting.RETENTION_PERIOD_DAYS.int()
                logger.info("Deleting posts created more than $retentionDays days ago")
                val deleted = PostStore.cleanupOldPosts(retentionDays)
                logger.info("Deleted $deleted posts created more than $retentionDays days ago")
            }
        } else {
            logger.info("Forwarding disabled, skipping...")
        }
    }

    private fun generateSlotMissingMessage(slotTime: String, user: String, lastPostTime: String, freeze: Int) =
        slotMissingMessageFormats.random()
            .replace("{slotTime}", slotTime)
            .replace("{user}", user)
            .replace("{lastPostTime}", lastPostTime)
            .replace("{freeze}", freeze.toString())




}
