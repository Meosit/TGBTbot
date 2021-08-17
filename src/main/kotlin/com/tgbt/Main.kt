package com.tgbt

import com.tgbt.bot.BotContext
import com.tgbt.bot.MessageContext
import com.tgbt.bot.editor.EditorButtonAction
import com.tgbt.bot.user.UserMessages
import com.tgbt.grammar.*
import com.tgbt.misc.escapeMarkdown
import com.tgbt.misc.trimToLength
import com.tgbt.post.PostStore
import com.tgbt.post.TgPreparedPost
import com.tgbt.post.toPost
import com.tgbt.settings.Setting.*
import com.tgbt.settings.SettingStore
import com.tgbt.settings.Settings
import com.tgbt.suggestion.SuggestionStatus
import com.tgbt.suggestion.SuggestionStore
import com.tgbt.suggestion.authorReference
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
import kotlinx.serialization.json.JsonConfiguration
import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

private val logger = LoggerFactory.getLogger("MainKt")

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.main() {
    val appUrl: String = System.getenv("APP_URL")
    val dbUrl: String = System.getenv("DATABASE_URL")
    val vkServiceToken: String = System.getenv("VK_SERVICE_TOKEN")
    val tgBotToken: String = System.getenv("TG_BOT_TOKEN")
    val telegraphApiToken: String = System.getenv("TELEGRAPH_TOKEN")
    val ownerIds: List<String> = if (System.getenv("OWNER_IDS").isNullOrBlank()) emptyList() else
        System.getenv("OWNER_IDS").split(',')

    val json = Json(JsonConfiguration.Stable.copy(ignoreUnknownKeys = true, isLenient = true), context = exprModule)

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
    val settings = Settings(SettingStore())
    insertDefaultSettings(settings, json)

    val tgMessageSender = TgMessageSender(httpClient, tgBotToken)
    val telegraphPostCreator = TelegraphPostCreator(httpClient, json, telegraphApiToken)
    val vkPostLoader = VkPostLoader(httpClient, vkServiceToken)

    val botContext = BotContext(json, ownerIds, postStore, suggestionStore, settings, tgMessageSender, telegraphPostCreator, vkPostLoader)

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
                val delayMinutes = settings[CHECK_PERIOD_MINUTES].toLong()
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
                val delayMinutes = settings[SUGGESTION_POLLING_DELAY_MINUTES].toLong()
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

suspend fun BotContext.forwardVkPosts(forcedByOwner: Boolean = false) {
    val enabled = settings[FORWARDING_ENABLED].toBoolean()
    if (enabled) {
        logger.info("Checking for new posts")
        val postsCount = settings[POST_COUNT_TO_LOAD].toInt()
        val condition = json.parse(Expr.serializer(), settings[CONDITION_EXPR])
        val communityId = settings[VK_COMMUNITY_ID].toLong()
        val footerMd = settings[FOOTER_MD]
        val sendStatus = settings[SEND_STATUS].toBoolean()
        val targetChannel = settings[TARGET_CHANNEL]

        val stats = mutableMapOf<String, Int>()
        val postsToForward = doNotThrow("Failed to load or parse VK posts") {
            vkPostLoader
                .load(postsCount, communityId)
                .filter { it.isPinned + it.markedAsAds == 0 }
                .map(VkPost::toPost)
                .also {
                    stats["total"] = it.size
                    logger.info("Loaded ${it.size} posts in total")
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
            doNotThrow("Post https://vk.com/wall${communityId}_${it.id} failed to send\nTo have it in telegram, post manually.\nAlso") {
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
                        "${stats["total"]} loaded in total\n" +
                        "${stats["condition"]} after filtering by condition\n" +
                        "Posts:\n> " + forwarded.joinToString("\n> ")
                logger.info(message)
                ownerIds.forEach { tgMessageSender.sendChatMessage(it, TgTextOutput(message), disableLinkPreview = true) }
            }
        }

        doNotThrow("Failed to clean up old posts from TG") {
            val retentionDays = settings[RETENTION_PERIOD_DAYS].toInt()
            logger.info("Deleting posts created more than $retentionDays days ago")
            val deleted = postStore.cleanupOldPosts(retentionDays)
            logger.info("Deleted $deleted posts created more than $retentionDays days ago")
        }
    } else {
        logger.info("Forwarding disabled, skipping...")
    }
}


suspend fun BotContext.forwardSuggestions(forcedByOwner: Boolean = false) {
    val targetChat = settings[EDITOR_CHAT_ID]
    val suggestionsEnabled = settings[SUGGESTIONS_ENABLED].toBoolean()
    val footerMd = settings[FOOTER_MD]
    if (suggestionsEnabled) {
        logger.info("Checking for posts which are ready for suggestion")
        doNotThrow("Failed to change suggestions status PENDING_USER_EDIT -> READY_FOR_SUGGESTION") {
            val suggestions = suggestionStore.findByStatus(SuggestionStatus.PENDING_USER_EDIT)
            for (suggestion in suggestions) {
                suggestionStore.update(suggestion.copy(status = SuggestionStatus.READY_FOR_SUGGESTION), byAuthor = true)
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
            if (settings[SEND_SUGGESTION_STATUS].toBoolean()) {
                ownerIds.forEach { tgMessageSender.sendChatMessage(it, TgTextOutput(message)) }
            }
        }
    } else {
        logger.info("Suggestions disabled, skipping...")
    }
}

suspend fun BotContext.notifyAboutForgottenSuggestions(forcedByOwner: Boolean = false): Int {
    val start = LocalTime.of(0, 0, 0, 0)
    val end = LocalTime.of(0, settings[SUGGESTION_POLLING_DELAY_MINUTES].toInt(), 0, 0)
    val now = LocalTime.from(Instant.now().atZone(ZoneId.of("Europe/Moscow")))
    var forgotten = 0
    if (forcedByOwner || (now.isAfter(start) && now.isBefore(end))) {
        logger.info("New day, checking for forgotten posts")
        val forgottenSuggestions = doNotThrow("Failed to fetch PENDING_EDITOR_REVIEW suggestions from DB") {
            suggestionStore.findByStatus(SuggestionStatus.PENDING_EDITOR_REVIEW)
        }
        logger.info("Currently there are ${forgottenSuggestions?.size} forgotten posts, notifying...")
        forgottenSuggestions?.forEach { suggestion ->
            doNotThrow("Failed to notify about forgotten post") {
                val hoursSinceCreated = Duration
                    .between(Instant.now(), suggestion.insertedTime.toInstant()).abs().toHours()
                if (hoursSinceCreated > 24) {
                    logger.info("Post from ${suggestion.authorName} created $hoursSinceCreated hours ago")
                    tgMessageSender.sendChatMessage(
                        suggestion.editorChatId.toString(),
                        TgTextOutput("Забытый пост, создан $hoursSinceCreated часов назад"),
                        replyMessageId = suggestion.editorMessageId
                    )
                    forgotten++
                }
            }
        }
    }
    return forgotten
}

private suspend fun BotContext.postScheduledSuggestions(footerMd: String): Int {
    val targetChannel = settings[TARGET_CHANNEL]
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
            if (settings[SEND_PROMOTION_FEEDBACK].toBoolean()) {
                tgMessageSender.sendChatMessage(suggestion.authorChatId.toString(),
                    TgTextOutput(UserMessages.postPromotedMessage.format(suggestion.postText.trimToLength(20, "..."))))
            }

        }
    }
    return scheduled
}

private suspend inline fun <T> BotContext.doNotThrow(message: String, block: () -> T?): T? = try {
    block()
} catch (e: Exception) {
    val clientError = (e as? ClientRequestException)?.response?.content?.readUTF8Line()
    val markdownText =
        "$message, please check logs, error message:\n`${clientError ?: e.message}`"
    logger.error(markdownText, e)
    val output = TgTextOutput(markdownText)
    ownerIds.forEach { tgMessageSender.sendChatMessage(it, output) }
    null
}

suspend fun BotContext.sendTelegramPost(targetChat: String, prepared: TgPreparedPost, keyboardMarkup: InlineKeyboardMarkup? = null): Message? {
    val usePhotoMode = settings[USE_PHOTO_MODE].toBoolean()
    val keyboardJson = keyboardMarkup?.let { json.stringify(InlineKeyboardMarkup.serializer(), keyboardMarkup) }
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
                    && prepared.maybeImage == null
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
        CONDITION_EXPR, json.stringify(
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
}
