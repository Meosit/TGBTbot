package com.tgbt

import com.tgbt.ban.BanStore
import com.tgbt.bot.BotContext
import com.tgbt.bot.MessageContext
import com.tgbt.bot.editor.EditorButtonAction
import com.tgbt.bot.owner.VkScheduleCommand
import com.tgbt.bot.owner.VkScheduleSlot
import com.tgbt.bot.user.UserMessages
import com.tgbt.grammar.*
import com.tgbt.misc.*
import com.tgbt.post.*
import com.tgbt.settings.Setting.*
import com.tgbt.settings.SettingStore
import com.tgbt.settings.Settings
import com.tgbt.suggestion.SuggestionStatus
import com.tgbt.suggestion.SuggestionStore
import com.tgbt.suggestion.authorReference
import com.tgbt.suggestion.postTextTeaser
import com.tgbt.telegram.*
import com.tgbt.telegram.output.TgImageOutput
import com.tgbt.telegram.output.TgTextOutput
import com.tgbt.vk.VkPost
import com.tgbt.vk.VkPostLoader
import com.vladsch.kotlin.jdbc.HikariCP
import com.vladsch.kotlin.jdbc.SessionImpl
import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.netty.*
import io.ktor.utils.io.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.plus
import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlin.math.min

val logger = LoggerFactory.getLogger("MainKt")

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.main() {
    val appUrl: String = System.getenv("APP_URL")
    val dbUrl: String = System.getenv("DATABASE_URL")
    val vkServiceToken: String = System.getenv("VK_SERVICE_TOKEN")
    val tgBotToken: String = System.getenv("TG_BOT_TOKEN")
    val telegraphApiToken: String = System.getenv("TELEGRAPH_TOKEN")
    val ownerIds: List<String> = if (System.getenv("OWNER_IDS").isNullOrBlank()) emptyList() else
        System.getenv("OWNER_IDS").split(',')

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        serializersModule += exprModule
    }

    install(ContentNegotiation) {
        json(json, ContentType.Application.Json)
    }
    install(DefaultHeaders)
    install(CallLogging)

    initializeDataSource(dbUrl)

    val httpClient = HttpClient {
        install(JsonFeature) {
            serializer = KotlinxSerializer(json)
        }
    }

    val postStore = PostStore()
    val suggestionStore = SuggestionStore()
    val banStore = BanStore()
    val settings = Settings(SettingStore())
    insertDefaultSettings(settings, json)

    val tgMessageSender = TgMessageSender(httpClient, tgBotToken)
    val telegraphPostCreator = TelegraphPostCreator(httpClient, json, telegraphApiToken)
    val vkPostLoader = VkPostLoader(httpClient, vkServiceToken)

    val botContext = BotContext(json, ownerIds, postStore, suggestionStore, banStore, settings, tgMessageSender, telegraphPostCreator, vkPostLoader)

    install(Routing) {
        post("/handle/$tgBotToken") {
            try {
                val update = call.receive<Update>()
                val msg = update.message ?: update.editedMessage
                logger.info("Received $update")
                when {
                    msg != null -> {
                        val msgContext = MessageContext(botContext, msg, isEdit = update.editedMessage != null)
                        msgContext.handleUpdate()
                    }
                    update.callbackQuery != null -> EditorButtonAction.handleActionCallback(botContext, update.callbackQuery)
                    else -> logger.info("Nothing useful, do nothing with this update")
                }
            } catch (e: Exception) {
                logger.error("Received exception while handling update: ${e.message}")
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                (e as? ClientRequestException)?.response?.content?.let {
                    logger.error(it.readUTF8Line())
                }
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
                botContext.forwardVkPosts()
                val delayMinutes = settings.long(CHECK_PERIOD_MINUTES)
                logger.info("Next post forward check after $delayMinutes minutes")
                val delayMillis = TimeUnit.MINUTES.toMillis(delayMinutes)
                selfPing(httpClient, appUrl)
                delay(delayMillis)
            } catch (e: Exception) {
                val message = "Unexpected error occurred while reposting, next try in 60 seconds, error message:\n`${e.message?.escapeMarkdown()}`"
                logger.error(message, e)
                (e as? ClientRequestException)?.response?.content?.let {
                    logger.error(it.readUTF8Line())
                }
                val output = TgTextOutput(message)
                ownerIds.forEach { tgMessageSender.sendChatMessage(it, output) }
                delay(60000)
            }
        } while (true)
    }
    launch {
        do {
            try {
                botContext.forwardSuggestions()
                val delayMinutes = settings.long(SUGGESTION_POLLING_DELAY_MINUTES)
                logger.info("Next suggestion polling after $delayMinutes minutes")
                val delayMillis = TimeUnit.MINUTES.toMillis(delayMinutes)
                selfPing(httpClient, appUrl)
                delay(delayMillis)
            } catch (e: Exception) {
                val message = "Unexpected error occurred while suggestions pooling, next try in 60 seconds, error message:\n`${e.message?.escapeMarkdown()}`"
                logger.error(message, e)
                (e as? ClientRequestException)?.response?.content?.let {
                    logger.error(it.readUTF8Line())
                }
                val output = TgTextOutput(message)
                ownerIds.forEach { tgMessageSender.sendChatMessage(it, output) }
                delay(60000)
            }
        } while (true)
    }
}

private suspend fun selfPing(httpClient: HttpClient, appUrl: String) {
    logger.info("Starting self-ping...")
    val response = httpClient.get<String>(appUrl)
    logger.info("Finished self-ping with response: '$response'")
}

suspend fun BotContext.sendLastDaySchedule(onlyMissed: Boolean = false) {
    doNotThrow("Failed to send last 24 hours schedule to TG") {
        val communityId = settings.long(VK_COMMUNITY_ID)
        val schedule = VkScheduleCommand.parseSchedule(settings)
        val slotError = settings.long(VK_SCHEDULE_ERROR_MINUTES)
        val now = zonedNow()

        val last24hoursPosts = vkPostLoader
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
        ownerIds.forEach { tgMessageSender.sendChatMessage(it, TgTextOutput(message.trim())) }
    }
}

suspend fun BotContext.forwardVkPosts(forcedByOwner: Boolean = false) {
    val enabled = settings.bool(FORWARDING_ENABLED)
    if (enabled) {
        logger.info("Checking for new posts")
        val postsCount = settings.int(POST_COUNT_TO_LOAD)
        val condition = json.decodeFromString(Expr.serializer(), settings.str(CONDITION_EXPR))
        val communityId = settings.long(VK_COMMUNITY_ID)
        val footerMd = settings.str(FOOTER_MD)
        val sendStatus = settings.bool(SEND_STATUS)
        val targetChannel = settings.str(TARGET_CHANNEL)
        val editorsChatId = settings.str(EDITOR_CHAT_ID)

        val stats = mutableMapOf<String, Int>()
        val lastPosts: MutableList<Post> = mutableListOf()
        val postsToForward = doNotThrow("Failed to load or parse VK posts") {
            vkPostLoader
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
                .filterNot { postStore.isPostedToTG(it) }
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
                if (postStore.insert(it)) {
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
                ownerIds.forEach { tgMessageSender.sendChatMessage(it, TgTextOutput(message), disableLinkPreview = true) }
            }
        }
        doNotThrow("Failed to send freeze notification") {
            if (lastPosts.isEmpty()) {
                return@doNotThrow
            }

            val vkFreezeIgnoreStart = if (settings.str(VK_FREEZE_IGNORE_START).isNotBlank()) LocalTime.parse(settings.str(VK_FREEZE_IGNORE_START)) else null
            val vkFreezeIgnoreEnd = if (settings.str(VK_FREEZE_IGNORE_END).isNotBlank()) LocalTime.parse(settings.str(VK_FREEZE_IGNORE_END)) else null
            val vkFreezeTimeout = settings.int(VK_FREEZE_TIMEOUT_MINUTES)
            val vkFreezeMentions = settings.str(VK_FREEZE_MENTIONS)
            val vkFreezeNotifyTimeout = settings.bool(NOTIFY_FREEZE_TIMEOUT)
            val vkFreezeNotifySchedule = settings.bool(NOTIFY_FREEZE_SCHEDULE)
            val vkFreezeSendStatus = settings.bool(SEND_FREEZE_STATUS)
            val slotError = settings.long(VK_SCHEDULE_ERROR_MINUTES)

            if (vkFreezeIgnoreStart != null && vkFreezeIgnoreEnd != null) {
                if (zonedNow().toLocalTime().inLocalRange(vkFreezeIgnoreStart, vkFreezeIgnoreEnd)) {
                    logger.info("Ignoring freeze notifications check due to ignore period enabled (${zonedNow().toLocalTime()} in $vkFreezeIgnoreStart..$vkFreezeIgnoreEnd")
                    return@doNotThrow
                }
            }

            val oldestPostTime = lastPosts.last().localTime
            val freeze = when {
                vkFreezeIgnoreEnd.inLocalRange(oldestPostTime, zonedNow().toLocalTime()) ->
                    Duration.between(vkFreezeIgnoreEnd, zonedNow().toLocalTime()).toMinutes().toInt()
                else -> stats["freeze"] ?: 0
            }

            val latestSlotTime = zonedNow().minusMinutes(min(freeze.toLong() - 1, slotError)).toLocalTime()
            val schedule = VkScheduleCommand.parseSchedule(settings)
            val involvedSlots = schedule.filter { slot -> slot.time.inLocalRange(oldestPostTime, latestSlotTime) }
            val latestSchedule = mergePostsWithSchedule(involvedSlots, lastPosts, slotError)

            if (latestSchedule.last().second == null && freeze < vkFreezeTimeout) {
                val slot = latestSchedule.last().first
                val post = latestSchedule.findLast { it.second != null }?.second
                if (vkFreezeNotifySchedule && slot != null && post != null) {
                    val message = generateSlotMissingMessage(slot.time.simpleFormatTime(), slot.user, post.localTime.simpleFormatTime(), freeze)
                    tgMessageSender.sendChatMessage(editorsChatId, TgTextOutput(message), disableLinkPreview = true)
                    if (vkFreezeSendStatus) ownerIds.forEach { tgMessageSender.sendChatMessage(it, TgTextOutput(message), disableLinkPreview = true) }
                }
            }

            val checkPeriod = settings.int(CHECK_PERIOD_MINUTES)
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
                if (vkFreezeNotifyTimeout) tgMessageSender.sendChatMessage(editorsChatId, TgTextOutput(message), disableLinkPreview = true)
                if (vkFreezeSendStatus) ownerIds.forEach { tgMessageSender.sendChatMessage(it, TgTextOutput(message), disableLinkPreview = true) }
            }

        }

        doNotThrow("Failed to clean up old posts from TG") {
            val retentionDays = settings.int(RETENTION_PERIOD_DAYS)
            logger.info("Deleting posts created more than $retentionDays days ago")
            val deleted = postStore.cleanupOldPosts(retentionDays)
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


suspend fun BotContext.forwardSuggestions(forcedByOwner: Boolean = false) {
    val targetChat = settings.str(EDITOR_CHAT_ID)
    val suggestionsEnabled = settings.bool(SUGGESTIONS_ENABLED)
    val footerMd = settings.str(FOOTER_MD)
    if (suggestionsEnabled) {
        logger.info("Checking for posts which are ready for suggestion")
        doNotThrow("Failed to change suggestions status PENDING_USER_EDIT -> READY_FOR_SUGGESTION") {
            val suggestions = suggestionStore.findByStatus(SuggestionStatus.PENDING_USER_EDIT)
            val editTimeMinutes = settings.long(USER_EDIT_TIME_MINUTES)
            for (suggestion in suggestions) {
                val diffMinutes = ChronoUnit.MINUTES.between(suggestion.insertedTime.toInstant(), Instant.now())
                if (diffMinutes >= editTimeMinutes) {
                    suggestionStore.update(suggestion.copy(status = SuggestionStatus.READY_FOR_SUGGESTION), byAuthor = true)
                }
            }
        }
        logger.info("Checking for new suggested posts")
        var forwarded = 0
        val suggestions = doNotThrow("Failed to fetch READY_FOR_SUGGESTION suggestions from DB") {
            suggestionStore.findByStatus(SuggestionStatus.READY_FOR_SUGGESTION)
        }
        suggestions?.forEach { suggestion ->
            doNotThrow("Failed to send single suggestion to editors group") {
                val post = TgPreparedPost(
                    suggestion.postText, suggestion.imageId, footerMarkdown = footerMd,
                    suggestionReference = suggestion.authorReference(false)
                )
                val editorMessage = sendTelegramPost(targetChat, post, EditorButtonAction.ACTION_KEYBOARD)
                if (editorMessage != null) {
                    suggestionStore.update(
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
            if (settings.bool(SEND_SUGGESTION_STATUS)) {
                ownerIds.forEach { tgMessageSender.sendChatMessage(it, TgTextOutput(message)) }
            }
        }
    } else {
        logger.info("Suggestions disabled, skipping...")
    }
}

suspend fun BotContext.notifyAboutForgottenSuggestions(force: Boolean = false, createdBeforeHours: Int = 0): Int {
    val start = LocalTime.of(0, 0, 0, 0)
    val end = LocalTime.of(0, settings.int(SUGGESTION_POLLING_DELAY_MINUTES), 0, 0)
    val now = zonedNow().toLocalTime()
    var forgotten = 0
    if (force || (now in start..end)) {
        if (!force) {
            logger.info("New day, checking for forgotten posts")
        }
        val forgottenSuggestions = doNotThrow("Failed to fetch PENDING_EDITOR_REVIEW suggestions from DB") {
            suggestionStore.findByStatus(SuggestionStatus.PENDING_EDITOR_REVIEW)
        }
        logger.info("Currently there are ${forgottenSuggestions?.size} forgotten posts, notifying...")
        forgottenSuggestions?.forEach { suggestion ->
            doNotThrow("Failed to notify about forgotten post") {
                val hoursSinceCreated = Duration
                    .between(Instant.now(), suggestion.insertedTime.toInstant()).abs().toHours()
                if (hoursSinceCreated > createdBeforeHours) {
                    logger.info("Post from ${suggestion.authorName} created $hoursSinceCreated hours ago")
                    tgMessageSender.sendChatMessage(
                        suggestion.editorChatId.toString(),
                        TgTextOutput("Пост ждёт обработки, создан $hoursSinceCreated часов назад"),
                        replyMessageId = suggestion.editorMessageId
                    )
                    delay(200)
                    forgotten++
                }
            }
        }
    }
    return forgotten
}

private suspend fun BotContext.postScheduledSuggestions(footerMd: String): Int {
    val targetChannel = settings.str(TARGET_CHANNEL)
    var scheduled = 0
    val suggestions = doNotThrow("Failed to fetch scheduled suggestions from DB") {
        suggestionStore.findReadyForSchedule()
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
            suggestionStore.removeByChatAndMessageId(suggestion.authorChatId, suggestion.authorMessageId, byAuthor = true)
            scheduled++
            if (settings.bool(SEND_PROMOTION_FEEDBACK)) {
                try {
                    tgMessageSender.sendChatMessage(suggestion.authorChatId.toString(),
                        TgTextOutput(UserMessages.postPromotedMessage.format(suggestion.postTextTeaser().escapeMarkdown())))
                } catch (e: ClientRequestException) {
                    if (e.response.status == HttpStatusCode.Forbidden) {
                        logger.info("Skipping promotion feedback for user ${suggestion.authorName} (${suggestion.authorChatId}): FORBIDDEN")
                    } else {
                        throw e
                    }
                }
            }
            logger.info("Posted scheduled post '${suggestion.postTextTeaser()}' from ${suggestion.authorName}")
        }
    }
    return scheduled
}

suspend inline fun <T> BotContext.doNotThrow(message: String, block: () -> T?): T? = try {
    block()
} catch (e: Exception) {
    val stack = e.stackTrace
    .filter { it.className.contains("com.tgbt") }
        .joinToString("\n") { "${it.className.replace("com.tgbt", "")}.${it.methodName}(${it.lineNumber})" }
        .ifBlank { "none" }
        .escapeMarkdown()
    val response = (e as? ClientRequestException)?.response
    val clientError = (response?.content?.readUTF8Line() ?: e.message)?.ifBlank { "none" }?.escapeMarkdown() ?: "none"
    val textParameter = (response?.let { it.request.url.parameters["text"].orEmpty() }.orEmpty()).trimToLength(400, "|<- truncated").ifBlank { "none" }.escapeMarkdown()
    val markdownText = "$message, please check logs, error message:\n`$clientError`\n\nText parameter (first 400 chars): `$textParameter`\n\nStacktrace:\n`$stack`"
    logger.error(markdownText, e)
    val output = TgTextOutput(markdownText)
    ownerIds.forEach { tgMessageSender.sendChatMessage(it, output) }
    null
}

suspend fun BotContext.sendTelegramPost(targetChat: String, prepared: TgPreparedPost, keyboardMarkup: InlineKeyboardMarkup? = null): Message? {
    val usePhotoMode = settings.bool(USE_PHOTO_MODE)
    val keyboardJson = keyboardMarkup?.let { json.encodeToString(InlineKeyboardMarkup.serializer(), keyboardMarkup) }
    return when {
        usePhotoMode && prepared.canBeSendAsImageWithCaption -> tgMessageSender
            .sendChatPhoto(
                targetChat,
                TgImageOutput(prepared.withoutImage, prepared.imageUrl(), keyboardJson)
            ).result
        prepared.withImage.length > 4096 -> {
            val (ok, error, result) = telegraphPostCreator.createPost(prepared)
            when {
                ok && result != null -> {
                    val output = TgTextOutput("Слишком длиннобугурт, поэтому читайте в телеграфе: [${result.title}](${result.url})${prepared.formattedFooter}", keyboardJson)
                    tgMessageSender.sendChatMessage(targetChat, output, disableLinkPreview = false).result
                }
                else -> {
                    val message = "Failed to create Telegraph post, please check logs, error message:\n`${error}`"
                    logger.error(message)
                    val output = TgTextOutput(message)
                    ownerIds.forEach { id -> tgMessageSender.sendChatMessage(id, output) }
                    null
                }
            }
        }
        else -> {
            // footer links should not be previewed.
            val disableLinkPreview = prepared.footerMarkdown.contains("https://")
                    && !prepared.text.contains("https://")
                    && !(prepared.maybeImage?.isImageUrl() ?: false)
            tgMessageSender.sendChatMessage(
                targetChat,
                TgTextOutput(prepared.withImage, keyboardJson),
                disableLinkPreview = disableLinkPreview
            ).result
        }
    }
}


private fun initializeDataSource(dbUrl: String) {
    val dbUri = URI(dbUrl)
    val (username: String, password: String) = dbUri.userInfo.split(":")
    val jdbcUrl = "jdbc:postgresql://${dbUri.host}:${dbUri.port}${dbUri.path}?sslmode=require"
    HikariCP.default(jdbcUrl, username, password)
    SessionImpl.defaultDataSource = { HikariCP.dataSource() }
    logger.info("JDBC url: $jdbcUrl")
}

private fun insertDefaultSettings(settings: Settings, json: Json) = with(settings) {
    putIfAbsent(TARGET_CHANNEL, "@tegebetetest")
    putIfAbsent(CHECK_PERIOD_MINUTES, "10")
    putIfAbsent(RETENTION_PERIOD_DAYS, "15")
    putIfAbsent(POST_COUNT_TO_LOAD, "300")
    putIfAbsent(VK_COMMUNITY_ID, "-57536014")
    putIfAbsent(FORWARDING_ENABLED, "false")
    putIfAbsent(USE_PHOTO_MODE, "true")
    putIfAbsent(FOOTER_MD, "")
    putIfAbsent(SEND_STATUS, "true")
    putIfAbsent(
        CONDITION_EXPR, json.encodeToString(
            Expr.serializer(),
            Or(
                Likes(ConditionalOperator.GREATER_OR_EQUAL, 1000),
                Reposts(ConditionalOperator.GREATER_OR_EQUAL, 15)
            )
        )
    )
    putIfAbsent(SUGGESTIONS_ENABLED, "true")
    putIfAbsent(EDITOR_CHAT_ID, "-1001519413163")
    putIfAbsent(USER_EDIT_TIME_MINUTES, "10")
    putIfAbsent(USER_SUGGESTION_DELAY_MINUTES, "30")
    putIfAbsent(SUGGESTION_POLLING_DELAY_MINUTES, "10")
    putIfAbsent(SEND_PROMOTION_FEEDBACK, "true")
    putIfAbsent(SEND_DELETION_FEEDBACK, "true")
    putIfAbsent(SEND_SUGGESTION_STATUS, "true")
    putIfAbsent(NOTIFY_FREEZE_TIMEOUT, "true")
    putIfAbsent(NOTIFY_FREEZE_SCHEDULE, "true")
    putIfAbsent(VK_FREEZE_TIMEOUT_MINUTES, "90")
    putIfAbsent(VK_FREEZE_IGNORE_START, "")
    putIfAbsent(VK_FREEZE_IGNORE_END, "")
    putIfAbsent(VK_FREEZE_MENTIONS, "anon")
    putIfAbsent(VK_SCHEDULE, "5:00 Улиточка")
    putIfAbsent(VK_SCHEDULE_ERROR_MINUTES, "5")
    putIfAbsent(SEND_FREEZE_STATUS, "true")
    putIfAbsent(GATEKEEPER, "anon")
}
