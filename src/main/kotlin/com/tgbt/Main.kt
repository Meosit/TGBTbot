package com.tgbt

import com.tgbt.bot.BotContext
import com.tgbt.bot.MessageContext
import com.tgbt.grammar.*
import com.tgbt.misc.escapeMarkdown
import com.tgbt.misc.trimToLength
import com.tgbt.post.PostStore
import com.tgbt.post.TgPreparedPost
import com.tgbt.post.toPost
import com.tgbt.settings.Setting.*
import com.tgbt.settings.SettingStore
import com.tgbt.settings.Settings
import com.tgbt.suggestion.SuggestionStore
import com.tgbt.telegram.TelegraphPostCreator
import com.tgbt.telegram.TgMessageSender
import com.tgbt.telegram.Update
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
import java.util.concurrent.TimeUnit

private val logger = LoggerFactory.getLogger("MainKt")

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.main() {
    val appUrl: String = System.getenv("APP_URL")
    val dbUrl: String = System.getenv("DATABASE_URL")
    val vkServiceToken: String = System.getenv("VK_SERVICE_TOKEN")
    val tgBotToken: String = System.getenv("TG_BOT_TOKEN")
    val telegraphApiToken: String = System.getenv("TELEGRAPH_TOKEN")
    val ownerIds: List<String> = (System.getenv("OWNER_IDS") ?: "").split(',')

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
                if (msg != null) {
                    val msgContext = MessageContext(botContext, msg, isEdit = update.editedMessage != null)
                    msgContext.handleUpdate()
                } else {
                    logger.info("No message, do nothing with this update")
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
                forwardVkPosts(botContext)
                val delayMinutes = settings[CHECK_PERIOD_MINUTES].toLong()
                logger.info("Next check after $delayMinutes minutes")
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
}

private suspend fun selfPing(httpClient: HttpClient, appUrl: String) {
    logger.info("Starting self-ping...")
    val response = httpClient.get<String>(appUrl)
    logger.info("Finished self-ping with response: '$response'")
}

suspend fun forwardVkPosts(
    bot: BotContext,
    forcedByOwner: Boolean = false
) = with (bot) {
    val enabled = settings[FORWARDING_ENABLED].toBoolean()
    if (enabled) {
        logger.info("Checking for new posts")
        val postsCount = settings[POST_COUNT_TO_LOAD].toInt()
        val condition = json.parse(Expr.serializer(), settings[CONDITION_EXPR])
        val communityId = settings[VK_COMMUNITY_ID].toLong()
        val targetChannel = settings[TARGET_CHANNEL]
        val usePhotoMode = settings[USE_PHOTO_MODE].toBoolean()
        val footerMd = settings[FOOTER_MD]
        val sendStatus = settings[SEND_STATUS].toBoolean()

        val stats = mutableMapOf<String, Int>()
        val postsToForward = try {
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
        } catch (e: Exception) {
            val message =
                "Failed to load or parse VK posts, please check logs, error message:\n`${e.message}`"
            logger.error(message, e)
            val output = TgTextOutput(message)
            ownerIds.forEach { tgMessageSender.sendChatMessage(it, output) }
            null
        }

        try {
            var forwarded = 0
            postsToForward?.forEach {
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
                    when {
                        usePhotoMode && prepared.canBeSendAsImageWithCaption -> tgMessageSender
                            .sendChatPhoto(
                                targetChannel,
                                TgImageOutput(prepared.withoutImage, prepared.imageUrl())
                            )
                        prepared.withImage.length > 4096 -> {
                            val (ok, error, result) = telegraphPostCreator.createPost(prepared)
                            when {
                                ok && result != null -> {
                                    val output = TgTextOutput("Слишком длиннобугурт, поэтому читайте в телеграфе: [${result.title}](${result.url})" + if (footerMd.isBlank()) "" else "\n\n$footerMd")
                                    tgMessageSender.sendChatMessage(targetChannel, output, disableLinkPreview = false)
                                }
                                else -> {
                                    val message = "Failed to create Telegraph post, please check logs, error message:\n`${error}`"
                                    logger.error(message)
                                    val output = TgTextOutput(message)
                                    ownerIds.forEach { id -> tgMessageSender.sendChatMessage(id, output) }
                                }
                            }
                        }
                        else -> {
                            // footer links should not be previewed.
                            val disableLinkPreview = footerMd.contains("https://")
                                    && !prepared.text.contains("https://")
                            tgMessageSender.sendChatMessage(
                                targetChannel,
                                TgTextOutput(prepared.withImage),
                                disableLinkPreview = disableLinkPreview
                            )
                        }
                    }
                    forwarded++
                }
            }
            if (forcedByOwner || (sendStatus && forwarded > 0)) {
                val message = "Right now forwarded $forwarded posts from VK to Telegram:\n" +
                        "${stats["total"]} loaded in total\n" +
                        "${stats["condition"]} after filtering by condition\n"
                logger.info(message)
                ownerIds.forEach { tgMessageSender.sendChatMessage(it, TgTextOutput(message)) }
            }
        } catch (e: Exception) {
            val clientError = (e as? ClientRequestException)?.response?.content?.readUTF8Line()
            val message =
                "Failed to send posts to TG, please check logs, error message:\n`${clientError ?: e.message}`"
            logger.error(message, e)
            val output = TgTextOutput(message)
            ownerIds.forEach { tgMessageSender.sendChatMessage(it, output) }
        }

        try {
            val retentionDays = settings[RETENTION_PERIOD_DAYS].toInt()
            logger.info("Deleting posts created more than $retentionDays days ago")
            val deleted = postStore.cleanupOldPosts(retentionDays)
            logger.info("Deleted $deleted posts created more than $retentionDays days ago")
        } catch (e: Exception) {
            val message = "Failed to send posts to TG, please check logs, error message:\n`${e.message}`"
            logger.error(message, e)
            val output = TgTextOutput(message)
            ownerIds.forEach { tgMessageSender.sendChatMessage(it, output) }
        }

    } else {
        logger.info("Forwarding disabled, skipping...")
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
    putIfAbsent(EDITOR_CHAT_ID, "-594088198")
    putIfAbsent(USER_EDIT_TIME_MINUTES, "10")
    putIfAbsent(USER_SUGGESTION_DELAY_MINUTES, "30")
    putIfAbsent(SUGGESTION_POLLING_DELAY_MINUTES, "10")
    putIfAbsent(SEND_PROMOTION_FEEDBACK, "true")
    putIfAbsent(SEND_DELETION_FEEDBACK, "true")
}
