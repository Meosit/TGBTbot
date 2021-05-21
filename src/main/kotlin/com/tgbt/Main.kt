package com.tgbt

import com.tgbt.bot.MessageContext
import com.tgbt.bot.owner.*
import com.tgbt.grammar.*
import com.tgbt.misc.escapeMarkdown
import com.tgbt.misc.trimToLength
import com.tgbt.post.PostStore
import com.tgbt.post.TgPreparedPost
import com.tgbt.post.toPost
import com.tgbt.settings.Setting
import com.tgbt.settings.Setting.*
import com.tgbt.settings.SettingStore
import com.tgbt.settings.Settings
import com.tgbt.telegram.TelegraphPostCreator
import com.tgbt.telegram.TgMessageSender
import com.tgbt.telegram.Update
import com.tgbt.telegram.output.TgImageOutput
import com.tgbt.telegram.output.TgTextOutput
import com.tgbt.vk.VkPost
import com.tgbt.vk.VkPostLoader
import com.vladsch.kotlin.jdbc.HikariCP
import com.vladsch.kotlin.jdbc.SessionImpl
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.client.HttpClient
import io.ktor.client.features.ClientRequestException
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.get
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.serialization.json
import io.ktor.server.netty.EngineMain
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import org.slf4j.LoggerFactory
import java.io.InputStreamReader
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
    val ownerIds: List<String> = System.getenv("OWNER_IDS").split(',')

    val json = Json(JsonConfiguration.Stable.copy(ignoreUnknownKeys = true), context = exprModule)

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
    val settings = Settings(SettingStore())
    insertDefaultSettings(settings, json)

    val tgMessageSender = TgMessageSender(httpClient, tgBotToken)
    val telegraphPostCreator = TelegraphPostCreator(httpClient, json, telegraphApiToken)
    val vkPostLoader = VkPostLoader(httpClient, vkServiceToken)


    install(Routing) {
        post("/handle/$tgBotToken") {
            try {
                val update = call.receive<Update>()
                val msg = update.message
                logger.info("Received $update")
                if (msg != null) {
                    val msgContext = MessageContext(postStore, settings, json, tgMessageSender, msg)
                    val command = msg.text
                    val chatId = msg.chat.id.toString()
                    if (chatId in ownerIds && command != null) {
                        when {
                            command.startsWith("/help") -> tgMessageSender
                                .sendChatMessage(chatId, TgTextOutput(loadResourceAsString("help.owner.md")))
                            command.startsWith("/settings") -> tgMessageSender
                                .sendChatMessage(
                                    chatId,
                                    TgTextOutput(
                                        Setting.values()
                                            .joinToString("\n") { "${it.name}: ${settings[it]}".escapeMarkdown() })
                                )
                            command.startsWith(CONDITION_COMMAND) -> msgContext.handleConditionCommand()
                            command.startsWith(FORWARDING_COMMAND) -> msgContext.handleForwardingCommand()
                            command.startsWith(CHANNEL_COMMAND) -> msgContext.handleChannelCommand()
                            command.startsWith(FOOTER_COMMAND) -> msgContext.handleFooterCommand()
                            command.startsWith(VK_ID_COMMAND) -> msgContext.handleVkIdCommand()
                            command.startsWith(CHECK_PERIOD_COMMAND) -> msgContext.handleCheckPeriodCommand()
                            command.startsWith(RETENTION_PERIOD_COMMAND) -> msgContext.handleRetentionPeriodCommand()
                            command.startsWith(POSTS_COUNT_COMMAND) -> msgContext.handlePostCountCommand()
                            command.startsWith(PHOTO_MODE_COMMAND) -> msgContext.handlePhotoModeCommand()
                            command.startsWith(SEND_STATUS_COMMAND) -> msgContext.handleSendStatusCommand()
                            command.startsWith("/forceforward") -> {
                                forwardVkPosts(settings, json, vkPostLoader, postStore, ownerIds, tgMessageSender, telegraphPostCreator, forcedByOwner = true)
                                ownerIds.forEach { tgMessageSender.sendChatMessage(it, TgTextOutput("Forward check finished")) }
                            }
                            else -> tgMessageSender.sendChatMessage(chatId, TgTextOutput("Unknown command"))
                        }
                    } else {
                        logger.info("Unknown user ${msg.chat.username} ${msg.chat.firstName} ${msg.chat.lastName} tried to use this bot")
                        // do nothing for now
                    }
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
                forwardVkPosts(settings, json, vkPostLoader, postStore, ownerIds, tgMessageSender, telegraphPostCreator)
                val delayMinutes = settings[CHECK_PERIOD_MINUTES].toLong()
                logger.info("Next check after $delayMinutes minutes")
                val delayMillis = TimeUnit.MINUTES.toMillis(delayMinutes)
                selfPing(httpClient, appUrl)
                delay(delayMillis)
            } catch (e: Exception) {
                val message = "Unexpected error occurred while reposting, next try in 60 seconds, error message:\n`${e.message}`"
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

private suspend fun forwardVkPosts(
    settings: Settings,
    json: Json,
    vkPostLoader: VkPostLoader,
    postStore: PostStore,
    ownerIds: List<String>,
    tgMessageSender: TgMessageSender,
    telegraphPostCreator: TelegraphPostCreator,
    forcedByOwner: Boolean = false
) {
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
                            val fullUrlMd = when {
                                ok && result != null ->
                                    "Слишком длиннобугурт, продолжение здесь: [${result.title}](${result.url})"
                                else -> {
                                    val errorOutput = TgTextOutput(
                                        "Failed to create Telegraph post: " +
                                                (error?.escapeMarkdown() ?: "Error field unspecified")
                                    )
                                    ownerIds.forEach { owner ->
                                        tgMessageSender.sendChatMessage(owner, errorOutput)
                                    }
                                    "Слишком длиннобугурт, а телеграф отвалился, полная версия в [посте ВК](https://vk.com/wall${communityId}_${it.id})"
                                }
                            }
                            val output =
                                TgTextOutput(prepared.withoutImage.trimToLength(4096, "…\n\n$fullUrlMd"))
                            tgMessageSender.sendChatMessage(targetChannel, output)
                        }
                        else -> {
                            // footer links should not be previewed.
                            val disableLinkPreview = footerMd.contains("https://")
                                    && !prepared.withImage.contains("https://")
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
    putIfAbsent(FORWARDING_ENABLED, "true")
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
}

private fun loadResourceAsString(resourceBaseName: String): String = Settings::class.java.classLoader
    .getResourceAsStream(resourceBaseName)
    .let { it ?: throw IllegalStateException("Null resource stream for $resourceBaseName") }
    .use { InputStreamReader(it).use(InputStreamReader::readText) }