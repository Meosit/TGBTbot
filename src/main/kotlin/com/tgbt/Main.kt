package com.tgbt

import com.tgbt.bot.MessageContext
import com.tgbt.bot.editor.EditorButtonAction
import com.tgbt.bot.owner.VkScheduleCommand
import com.tgbt.bot.owner.VkScheduleSlot
import com.tgbt.bot.user.UserMessages
import com.tgbt.grammar.*
import com.tgbt.misc.*
import com.tgbt.post.*
import com.tgbt.settings.Setting.*
import com.tgbt.suggestion.*
import com.tgbt.telegram.*
import com.tgbt.telegram.api.*
import com.tgbt.telegram.output.TgImageOutput
import com.tgbt.telegram.output.TgTextOutput
import com.tgbt.vk.VkPost
import com.tgbt.vk.VkPostLoader
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.jetty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.plus
import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlin.math.min

val logger = LoggerFactory.getLogger("MainKt")


val BotJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    serializersModule += exprModule
}


val BotHttpClient = HttpClient {
    install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
        json(BotJson)
    }
}

val BotToken: String = System.getenv("TG_BOT_TOKEN")

val BotOwnerIds: List<String> = if (System.getenv("OWNER_IDS").isNullOrBlank()) emptyList() else
    System.getenv("OWNER_IDS").split(',')

val TelegraphToken: String = System.getenv("TELEGRAPH_TOKEN")
val VkToken: String = System.getenv("VK_SERVICE_TOKEN")
val DatabaseUrl: String = System.getenv("DATABASE_URL")

fun main(args: Array<String>) = EngineMain.main(args)

@Suppress("unused")
fun Application.main() {
    install(DefaultHeaders)
    install(CallLogging)
    install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
        json(BotJson)
    }

    install(Routing) {
        post("/handle/$BotToken") {
            try {
                val update = call.receive<Update>()
                val msg = update.message ?: update.editedMessage
                when {
                    msg != null -> {
                        val msgContext = MessageContext(msg, isEdit = update.editedMessage != null)
                        msgContext.handleUpdate()
                    }

                    update.callbackQuery != null -> {
                        logger.info(
                            "Callback (${update.callbackQuery.from.simpleRef})${update.callbackQuery.data} to ${
                                update.callbackQuery.message?.anyText?.trimToLength(
                                    50
                                )
                            }"
                        )
                        EditorButtonAction.handleActionCallback(update.callbackQuery)
                    }

                    else -> logger.info("Nothing useful, do nothing with this update")
                }
            } catch (e: Exception) {
                logger.error("Received exception while handling update: ${e.message}")
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                (e as? ClientRequestException)?.response?.bodyAsText()?.let { logger.error(it) }
                logger.error("Uncaught exception: $sw")
            }
            call.respond(HttpStatusCode.OK)
        }
        get("/") {
            call.request
            call.respondText("What are you looking here?", ContentType.Text.Html)
        }
    }

    launch {
        do {
            try {
                forwardVkPosts()
                val delayMinutes = CHECK_PERIOD_MINUTES.long()
                logger.info("Next post forward check after $delayMinutes minutes")
                val delayMillis = TimeUnit.MINUTES.toMillis(delayMinutes)
                delay(delayMillis)
            } catch (e: Exception) {
                val message =
                    "Unexpected error occurred while reposting, next try in 60 seconds, error message:\n`${e.message?.escapeMarkdown()}`"
                logger.error(message, e)
                (e as? ClientRequestException)?.response?.bodyAsText()?.let { logger.error(it) }
                val output = TgTextOutput(message)
                BotOwnerIds.forEach { TelegramClient.sendChatMessage(it, output) }
                delay(60000)
            }
        } while (true)
    }
    launch {
        do {
            try {
                forwardSuggestions()
                val delayMinutes = SUGGESTION_POLLING_DELAY_MINUTES.long()
                logger.info("Next suggestion polling after $delayMinutes minutes")
                val delayMillis = TimeUnit.MINUTES.toMillis(delayMinutes)
                delay(delayMillis)
            } catch (e: Exception) {
                val message =
                    "Unexpected error occurred while suggestions pooling, next try in 60 seconds, error message:\n`${e.message?.escapeMarkdown()}`"
                logger.error(message, e)
                (e as? ClientRequestException)?.response?.bodyAsText()?.let { logger.error(it) }
                val output = TgTextOutput(message)
                BotOwnerIds.forEach { TelegramClient.sendChatMessage(it, output) }
                delay(60000)
            }
        } while (true)
    }
}

suspend fun sendLastDaySchedule(onlyMissed: Boolean = false) {
    doNotThrow("Failed to send last 24 hours schedule to TG") {
        val communityId = VK_COMMUNITY_ID.long()
        val schedule = VkScheduleCommand.parseSchedule()
        val slotError = VK_SCHEDULE_ERROR_MINUTES.long()
        val now = zonedNow()

        val last24hoursPosts = VkPostLoader
            .load(50, communityId)
            .filter { it.isPinned + it.markedAsAds == 0 }
            .map(VkPost::toPost)
            .filterNot { it.text.contains("#БТnews") }
            .filter { Duration.between(it.zonedTime, now).toMinutes() < 24 * 60 }

        val merged = mergePostsWithSchedule(schedule, last24hoursPosts, slotError)

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

suspend fun forwardVkPosts(forcedByOwner: Boolean = false) {
    val enabled = FORWARDING_ENABLED.bool()
    if (enabled) {
        logger.info("Checking for new posts")
        val postsCount = POST_COUNT_TO_LOAD.int()
        val condition = BotJson.decodeFromString(Expr.serializer(), CONDITION_EXPR.str())
        val communityId = VK_COMMUNITY_ID.long()
        val footerMd = FOOTER_MD.str()
        val sendStatus = SEND_STATUS.bool()
        val targetChannel = TARGET_CHANNEL.str()
        val editorsChatId = EDITOR_CHAT_ID.str()

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
                        "Inserted new post https://vk.com/wall${communityId}_${it.id} '${
                            it.text.trimToLength(
                                50,
                                "…"
                            )
                        }'"
                    )
                    sendTelegramPost(targetChannel, prepared)
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

            val vkFreezeIgnoreStart = if (VK_FREEZE_IGNORE_START.str().isNotBlank()) LocalTime.parse(VK_FREEZE_IGNORE_START.str()) else null
            val vkFreezeIgnoreEnd = if (VK_FREEZE_IGNORE_END.str().isNotBlank()) LocalTime.parse(VK_FREEZE_IGNORE_END.str()) else null
            val vkFreezeTimeout = VK_FREEZE_TIMEOUT_MINUTES.int()
            val vkFreezeMentions = VK_FREEZE_MENTIONS.str()
            val vkFreezeNotifyTimeout = NOTIFY_FREEZE_TIMEOUT.bool()
            val vkFreezeNotifySchedule = NOTIFY_FREEZE_SCHEDULE.bool()
            val vkFreezeSendStatus = SEND_FREEZE_STATUS.bool()
            val slotError = VK_SCHEDULE_ERROR_MINUTES.long()

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
            val latestSchedule = mergePostsWithSchedule(involvedSlots, lastPosts, slotError)
            val checkPeriod = CHECK_PERIOD_MINUTES.int()

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
                        post != null -> "- ${post.localTime.simpleFormatTime()}: '${post.text.trimToLength(20, "…").replace('\n', ' ').escapeMarkdown()}'"
                        slot != null -> "- ${slot.time.simpleFormatTime()}: *Слот пропущен ${slot.user}*"
                        else -> "- Эта строчка не должна здесь быть..."
                    }
                }
                if (vkFreezeNotifyTimeout) TelegramClient.sendChatMessage(editorsChatId, TgTextOutput(message), disableLinkPreview = true)
                if (vkFreezeSendStatus) BotOwnerIds.forEach { TelegramClient.sendChatMessage(it, TgTextOutput(message), disableLinkPreview = true) }
            }

        }

        doNotThrow("Failed to clean up old posts from TG") {
            val retentionDays = RETENTION_PERIOD_DAYS.int()
            logger.info("Deleting posts created more than $retentionDays days ago")
            val deleted = PostStore.cleanupOldPosts(retentionDays)
            logger.info("Deleted $deleted posts created more than $retentionDays days ago")
        }
    } else {
        logger.info("Forwarding disabled, skipping...")
    }
}

private fun mergePostsWithSchedule(
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

private fun generateSlotMissingMessage(slotTime: String, user: String, lastPostTime: String, freeze: Int) =
    slotMissingMessageFormats.random()
        .replace("{slotTime}", slotTime)
        .replace("{user}", user)
        .replace("{lastPostTime}", lastPostTime)
        .replace("{freeze}", freeze.toString())


suspend fun forwardSuggestions(forcedByOwner: Boolean = false) {
    val targetChat = EDITOR_CHAT_ID.str()
    val suggestionsEnabled = SUGGESTIONS_ENABLED.bool()
    val footerMd = FOOTER_MD.str()
    if (suggestionsEnabled) {
        logger.info("Checking for posts which are ready for suggestion")
        doNotThrow("Failed to change suggestions status PENDING_USER_EDIT -> READY_FOR_SUGGESTION") {
            val suggestions = SuggestionStore.findByStatus(SuggestionStatus.PENDING_USER_EDIT)
            val editTimeMinutes = USER_EDIT_TIME_MINUTES.long()
            for (suggestion in suggestions) {
                val diffMinutes = ChronoUnit.MINUTES.between(suggestion.insertedTime.toInstant(), Instant.now())
                if (diffMinutes >= editTimeMinutes) {
                    SuggestionStore.update(
                        suggestion.copy(status = SuggestionStatus.READY_FOR_SUGGESTION),
                        byAuthor = true
                    )
                }
            }
        }
        logger.info("Checking for new suggested posts")
        var forwarded = 0
        val suggestions = doNotThrow("Failed to fetch READY_FOR_SUGGESTION suggestions from DB") {
            SuggestionStore.findByStatus(SuggestionStatus.READY_FOR_SUGGESTION)
        }
        suggestions?.forEach { suggestion ->
            doNotThrow("Failed to send single suggestion to editors group") {
                val post = TgPreparedPost(
                    suggestion.postText, suggestion.imageId, footerMarkdown = footerMd,
                    suggestionReference = suggestion.authorReference(false)
                )
                val editorMessage = sendTelegramPost(targetChat, post, EditorButtonAction.ACTION_KEYBOARD)
                if (editorMessage != null) {
                    SuggestionStore.update(
                        suggestion.copy(
                            editorChatId = editorMessage.chat.id,
                            editorMessageId = editorMessage.id,
                            status = SuggestionStatus.PENDING_EDITOR_REVIEW
                        ),
                        byAuthor = true
                    )
                }
                forwarded++
            }
        }
        val forgotten = notifyAboutForgottenSuggestions(forcedByOwner)
        val scheduled = postScheduledSuggestions(footerMd)
        if (forcedByOwner || forwarded > 0 || forgotten > 0 || scheduled > 0) {
            val message = "*SUGGESTIONS*\n" +
                    "\nRight now forwarded $forwarded suggestions from users." +
                    "\nEditors forgot to review $forgotten posts." +
                    "\nPosted $scheduled scheduled posts."
            logger.info(message)
            if (SEND_SUGGESTION_STATUS.bool()) {
                BotOwnerIds.forEach { TelegramClient.sendChatMessage(it, TgTextOutput(message)) }
            }
        }
    } else {
        logger.info("Suggestions disabled, skipping...")
    }
}

suspend fun notifyAboutForgottenSuggestions(force: Boolean = false, createdBeforeHours: Int = 0): Int {
    val start = LocalTime.of(0, 0, 0, 0)
    val end = LocalTime.of(0, SUGGESTION_POLLING_DELAY_MINUTES.int(), 0, 0)
    val now = zonedNow().toLocalTime()
    var forgotten = 0
    if (force || (now in start..end)) {
        if (!force) {
            logger.info("New day, checking for forgotten posts")
        }
        val forgottenSuggestions = doNotThrow("Failed to fetch PENDING_EDITOR_REVIEW suggestions from DB") {
            SuggestionStore.findByStatus(SuggestionStatus.PENDING_EDITOR_REVIEW)
        }
        logger.info("Currently there are ${forgottenSuggestions?.size} forgotten posts, notifying...")
        forgottenSuggestions?.forEach { suggestion ->
            doNotThrow("Failed to notify about forgotten post") {
                val hoursSinceCreated = Duration
                    .between(Instant.now(), suggestion.insertedTime.toInstant()).abs().toHours()
                if (hoursSinceCreated > createdBeforeHours) {
                    logger.info("Post from ${suggestion.authorName} created $hoursSinceCreated hours ago")
                    sendPendingReviewNotification(suggestion, hoursSinceCreated)
                    delay(3100)
                    forgotten++
                }
            }
        }
    }
    return forgotten
}

private suspend fun sendPendingReviewNotification(
    suggestion: UserSuggestion,
    hoursSinceCreated: Long
) = TelegramClient.sendChatMessage(
    suggestion.editorChatId.toString(),
    TgTextOutput("Пост ждёт обработки, создан $hoursSinceCreated часов назад"),
    replyMessageId = suggestion.editorMessageId
)

private suspend fun postScheduledSuggestions(footerMd: String): Int {
    val targetChannel = TARGET_CHANNEL.str()
    var scheduled = 0
    val suggestions = doNotThrow("Failed to fetch scheduled suggestions from DB") {
        SuggestionStore.findReadyForSchedule()
    }
    logger.info("Currently there are ${suggestions?.size} ready for schedule posts, notifying...")
    suggestions?.forEach { suggestion ->
        doNotThrow("Failed to post scheduled suggestion to channel") {
            val anonymous = suggestion.status != SuggestionStatus.SCHEDULE_PUBLICLY
            val post = TgPreparedPost(
                suggestion.postText, suggestion.imageId, footerMarkdown = footerMd,
                suggestionReference = suggestion
                    .authorReference(anonymous)
            )
            sendTelegramPost(targetChannel, post)
            SuggestionStore.removeByChatAndMessageId(
                suggestion.authorChatId,
                suggestion.authorMessageId,
                byAuthor = true
            )
            scheduled++
            TelegramClient.sendChatMessage(
                suggestion.authorChatId.toString(),
                TgTextOutput(
                    UserMessages.postPromotedMessage.format(
                        suggestion.postTextTeaser().escapeMarkdown()
                    )
                )
            )
            logger.info("Posted scheduled post '${suggestion.postTextTeaser()}' from ${suggestion.authorName}")
        }
    }
    return scheduled
}

suspend inline fun <T> doNotThrow(message: String, block: () -> T?): T? = try {
    block()
} catch (e: Exception) {
    val response = (e as? ClientRequestException)?.response
    val clientError = (response?.bodyAsText() ?: e.message)?.ifBlank { "none" }?.escapeMarkdown() ?: "none"
    val textParameter =
        (response?.let { it.request.url.parameters["text"].orEmpty() }.orEmpty()).trimToLength(400, "|<- truncated")
            .ifBlank { "none" }.escapeMarkdown()
    val markdownText =
        "$message, please check logs, error message:\n`$clientError`\n\nText parameter (first 400 chars): `$textParameter`\n\n"
    logger.error(markdownText, e)
    val output = TgTextOutput(markdownText)
    BotOwnerIds.forEach { TelegramClient.sendChatMessage(it, output) }
    null
}

suspend fun sendTelegramPost(
    targetChat: String,
    prepared: TgPreparedPost,
    keyboardMarkup: InlineKeyboardMarkup? = null
): Message? {
    val usePhotoMode = USE_PHOTO_MODE.bool()
    val keyboardJson = keyboardMarkup?.let { BotJson.encodeToString(InlineKeyboardMarkup.serializer(), keyboardMarkup) }
    return when {
        usePhotoMode && prepared.canBeSendAsImageWithCaption -> TelegramClient
            .sendChatPhoto(
                targetChat,
                TgImageOutput(prepared.withoutImage, prepared.imageUrl(), keyboardJson)
            ).result

        prepared.withImage.length > 4096 -> {
            val (ok, error, result) = TelegraphPostCreator.createPost(prepared)
            when {
                ok && result != null -> {
                    val output = TgTextOutput(
                        "Слишком длиннобугурт, поэтому читайте в телеграфе: [${result.title}](${result.url})${prepared.formattedFooter}",
                        keyboardJson
                    )
                    TelegramClient.sendChatMessage(targetChat, output, disableLinkPreview = false).result
                }

                else -> {
                    val message = "Failed to create Telegraph post, please check logs, error message:\n`${error}`"
                    logger.error(message)
                    val output = TgTextOutput(message)
                    BotOwnerIds.forEach { id -> TelegramClient.sendChatMessage(id, output) }
                    null
                }
            }
        }

        else -> {
            // footer links should not be previewed.
            val disableLinkPreview = prepared.footerMarkdown.contains("https://")
                    && !prepared.text.contains("https://")
                    && !(prepared.maybeImage?.isImageUrl() ?: false)
            TelegramClient.sendChatMessage(
                targetChat,
                TgTextOutput(prepared.withImage, keyboardJson),
                disableLinkPreview = disableLinkPreview
            ).result
        }
    }
}
