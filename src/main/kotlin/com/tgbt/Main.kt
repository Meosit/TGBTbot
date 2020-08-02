package com.tgbt

import com.github.h0tk3y.betterParse.grammar.tryParseToEnd
import com.github.h0tk3y.betterParse.parser.ErrorResult
import com.github.h0tk3y.betterParse.parser.Parsed
import com.tgbt.grammar.*
import com.tgbt.post.PostStore
import com.tgbt.post.toPost
import com.tgbt.settings.Setting.*
import com.tgbt.settings.SettingStore
import com.tgbt.settings.Settings
import com.tgbt.telegram.TgMessageSender
import com.tgbt.telegram.Update
import com.tgbt.telegram.output.TgImagePostOutput
import com.tgbt.telegram.output.TgLongPostOutput
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
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URI
import java.util.concurrent.TimeUnit

private val logger = LoggerFactory.getLogger("MainKt")

private const val CONDITION_COMMAND = "/condition "
private const val FORWARDING_COMMAND = "/forwarding "
private const val CHANNEL_COMMAND = "/channel "
private const val VK_ID_COMMAND = "/vkid "
private const val CHECK_PERIOD_COMMAND = "/check "
private const val RETENTION_PERIOD_COMMAND = "/retention "
private const val POSTS_COUNT_COMMAND = "/count "
private const val PHOTO_MODE_COMMAND = "/photomode "

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.main() {
    val dbUrl: String = System.getenv("DATABASE_URL")
    val vkServiceToken: String = System.getenv("VK_SERVICE_TOKEN")
    val tgBotToken: String = System.getenv("TG_BOT_TOKEN")
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
    val vkPostLoader = VkPostLoader(httpClient, vkServiceToken)


    install(Routing) {
        post("/handle/$tgBotToken") {
            try {
                val update = call.receive<Update>()
                val msg = update.message
                logger.info("Recieved $update")
                if (msg != null) {
                    val command = msg.text
                    val chatId = msg.chat.id.toString()
                    if (chatId in ownerIds && command != null) {
                        when {
                            command.startsWith(CONDITION_COMMAND) -> handleConditionCommand(
                                command,
                                json,
                                settings,
                                tgMessageSender,
                                chatId,
                                msg.messageId
                            )
                            command.startsWith(FORWARDING_COMMAND) -> handleForwardingCommand(
                                command,
                                settings,
                                tgMessageSender,
                                chatId,
                                msg.messageId
                            )
                            command.startsWith(CHANNEL_COMMAND) -> handleChannelCommand(
                                command,
                                settings,
                                tgMessageSender,
                                chatId,
                                msg.messageId
                            )
                            command.startsWith(VK_ID_COMMAND) -> handleVkIdCommand(
                                command,
                                settings,
                                tgMessageSender,
                                chatId,
                                msg.messageId
                            )
                            command.startsWith(CHECK_PERIOD_COMMAND) -> handleCheckPeriodCommand(
                                command,
                                settings,
                                tgMessageSender,
                                chatId,
                                msg.messageId
                            )
                            command.startsWith(RETENTION_PERIOD_COMMAND) -> handleRetentionPeriodCommand(
                                command,
                                settings,
                                tgMessageSender,
                                chatId,
                                msg.messageId
                            )
                            command.startsWith(POSTS_COUNT_COMMAND) -> handlePostCountCommand(
                                command,
                                settings,
                                tgMessageSender,
                                chatId,
                                msg.messageId
                            )
                            command.startsWith(PHOTO_MODE_COMMAND) -> handlePhotoModeCommand(
                                command,
                                settings,
                                tgMessageSender,
                                chatId,
                                msg.messageId
                            )
                            else -> tgMessageSender.sendChatMessage(
                                chatId,
                                TgTextOutput("Unknown command")
                            )
                        }
                    } else {
                        logger.info("Unknown user ${msg.chat.username} ${msg.chat.firstName} ${msg.chat.lastName} tried to use this bot")
                        // do nothing for now
                    }
                }
            } catch (e: Exception) {
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
                val enabled = settings[FORWARDING_ENABLED].toBoolean()
                if (enabled) {
                    logger.info("Checking for new posts")
                    val postsCount = settings[POST_COUNT_TO_LOAD].toInt()
                    val condition = json.parse(Expr.serializer(), settings[CONDITION_EXPR])
                    val communityId = settings[VK_COMMUNITY_ID].toLong()
                    val targetChannel = settings[TARGET_CHANNEL]
                    val usePhotoMode = settings[USE_PHOTO_MODE].toBoolean()

                    val postsToForward = try {
                        vkPostLoader
                            .load(postsCount, communityId)
                            .map(VkPost::toPost)
                            .filter { condition.evaluate(it.stats) }
                            .also { logger.info("${it.size} posts left after filtering by forward condition") }
                            .filterNot { postStore.isPostedToTG(it) }
                            .also { logger.info("${it.size} after checking for already forwarded posts") }
                    } catch (e: Exception) {
                        val message =
                            "Failed to load or parse VK posts, please check logs, error message:\n`${e.message}`"
                        logger.error(message, e)
                        val output = TgTextOutput(message)
                        ownerIds.forEach { tgMessageSender.sendChatMessage(it, output) }
                        null
                    }

                    try {
                        postsToForward?.forEach {
                            when {
                                usePhotoMode && it.imageUrl != null && it.text.length in 0..1024 ->
                                    tgMessageSender.sendChatPhoto(targetChannel, TgImagePostOutput(it))
                                else -> tgMessageSender.sendChatMessage(targetChannel, TgLongPostOutput(it))
                            }
                        }
                    } catch (e: Exception) {
                        val message = "Failed to send posts to TG, please check logs, error message:\n`${e.message}`"
                        logger.error(message, e)
                        val output = TgTextOutput(message)
                        ownerIds.forEach { tgMessageSender.sendChatMessage(it, output) }
                    }

                } else {
                    logger.info("Forwarding disabled, skipping...")
                }
                val delayMinutes = settings[CHECK_PERIOD_MINUTES].toLong()
                logger.info("Next check after for $delayMinutes minutes")
                val delayMillis = TimeUnit.MINUTES.toMillis(delayMinutes)
                delay(delayMillis)
            } catch (e: Exception) {
                val message =
                    "Unexpected error occurred while reposting, next try in 60 seconds, error message:\n`${e.message}`"
                logger.error(message, e)
                val output = TgTextOutput(message)
                ownerIds.forEach { tgMessageSender.sendChatMessage(it, output) }
                delay(6000)
            }
        } while (isActive)
    }
}

private suspend fun handleConditionCommand(
    command: String,
    json: Json,
    settings: Settings,
    tgMessageSender: TgMessageSender,
    chatId: String,
    messageId: Long
) {
    when (val maybeExpr = ConditionGrammar.tryParseToEnd(command.removePrefix(CONDITION_COMMAND))) {
        is Parsed -> {
            val exprJson = json.stringify(Expr.serializer(), maybeExpr.value)
            settings[CONDITION_EXPR] = exprJson
            val markdownText = if (exprJson == settings[CONDITION_EXPR])
                "Condition updated successfully" else "Failed to save condition to database"
            tgMessageSender.sendChatMessage(chatId, TgTextOutput(markdownText), messageId)
        }
        is ErrorResult -> {
            tgMessageSender.sendChatMessage(
                chatId,
                TgTextOutput("Invalid condition syntax"), messageId
            )
        }
    }
}

private suspend fun handleForwardingCommand(
    command: String,
    settings: Settings,
    tgMessageSender: TgMessageSender,
    chatId: String,
    messageId: Long
) {
    when (val value = command.removePrefix(FORWARDING_COMMAND)) {
        "" -> {
            tgMessageSender.sendChatMessage(chatId, TgTextOutput("Argument expected"), messageId)
        }
        "true", "false" -> {
            settings[FORWARDING_ENABLED] = value
            val markdownText = if (value == "true")
                "VK -> TG Post forwarding enabled" else "VK -> TG Post forwarding disabled"
            tgMessageSender.sendChatMessage(chatId, TgTextOutput(markdownText), messageId)
        }
        else -> {
            tgMessageSender.sendChatMessage(chatId, TgTextOutput("Invalid argument '$value'"), messageId)
        }
    }
}

private suspend fun handlePhotoModeCommand(
    command: String,
    settings: Settings,
    tgMessageSender: TgMessageSender,
    chatId: String,
    messageId: Long
) {
    when (val value = command.removePrefix(PHOTO_MODE_COMMAND)) {
        "" -> {
            tgMessageSender.sendChatMessage(chatId, TgTextOutput("Argument expected"), messageId)
        }
        "true", "false" -> {
            settings[USE_PHOTO_MODE] = value
            val markdownText = if (value == "true")
                "Posts with image and text length up to 1024 chars to be sent via image with caption"
            else "Posts with image and text length up to 1024 chars to be sent via message with imaage link"
            tgMessageSender.sendChatMessage(chatId, TgTextOutput(markdownText), messageId)
        }
        else -> {
            tgMessageSender.sendChatMessage(chatId, TgTextOutput("Invalid argument '$value'"), messageId)
        }
    }
}

private suspend fun handleChannelCommand(
    command: String,
    settings: Settings,
    tgMessageSender: TgMessageSender,
    chatId: String,
    messageId: Long
) {
    when (val value = command.removePrefix(CHANNEL_COMMAND)) {
        "" -> {
            tgMessageSender.sendChatMessage(chatId, TgTextOutput("Argument expected"), messageId)
        }
        else -> {
            settings[TARGET_CHANNEL] = value
            val markdownText =
                "Target channel is set to '$value' (please ensure that it's ID or username which starts from '@')"
            tgMessageSender.sendChatMessage(chatId, TgTextOutput(markdownText), messageId)
        }
    }
}

private suspend fun handleVkIdCommand(
    command: String,
    settings: Settings,
    tgMessageSender: TgMessageSender,
    chatId: String,
    messageId: Long
) {
    when (val value = command.removePrefix(VK_ID_COMMAND)) {
        "" -> {
            tgMessageSender.sendChatMessage(chatId, TgTextOutput("Argument expected"), messageId)
        }
        else -> {
            val markdownText = if (value.toLongOrNull() != null) {
                settings[VK_COMMUNITY_ID] = value
                "VK community ID now is $value, please ensure that it's correct"
            } else {
                "Integer value expected, got '$value'"
            }
            tgMessageSender.sendChatMessage(chatId, TgTextOutput(markdownText), messageId)
        }
    }
}


private suspend fun handleCheckPeriodCommand(
    command: String,
    settings: Settings,
    tgMessageSender: TgMessageSender,
    chatId: String,
    messageId: Long
) {
    when (val value = command.removePrefix(CHECK_PERIOD_COMMAND)) {
        "" -> {
            tgMessageSender.sendChatMessage(chatId, TgTextOutput("Argument expected"), messageId)
        }
        else -> {
            val markdownText = if (value.toIntOrNull() != null) {
                if (value.toInt() in 1..60) {
                    settings[CHECK_PERIOD_MINUTES] = value
                    "VK would be checked every $value minutes"
                } else {
                    "Value must be between 1 and 60"
                }
            } else {
                "Integer value expected, got '$value'"
            }
            tgMessageSender.sendChatMessage(chatId, TgTextOutput(markdownText), messageId)
        }
    }
}


private suspend fun handleRetentionPeriodCommand(
    command: String,
    settings: Settings,
    tgMessageSender: TgMessageSender,
    chatId: String,
    messageId: Long
) {
    when (val value = command.removePrefix(RETENTION_PERIOD_COMMAND)) {
        "" -> {
            tgMessageSender.sendChatMessage(chatId, TgTextOutput("Argument expected"), messageId)
        }
        else -> {
            val markdownText = if (value.toIntOrNull() != null) {
                if (value.toInt() > 1) {
                    settings[RETENTION_PERIOD_DAYS] = value
                    "Posts older than $value will be deleted from the database"
                } else {
                    "Retention period must be set at minimum 1 day"
                }
            } else {
                "Integer value expected, got '$value'"
            }
            tgMessageSender.sendChatMessage(chatId, TgTextOutput(markdownText), messageId)
        }
    }
}

private suspend fun handlePostCountCommand(
    command: String,
    settings: Settings,
    tgMessageSender: TgMessageSender,
    chatId: String,
    messageId: Long
) {
    when (val value = command.removePrefix(POSTS_COUNT_COMMAND)) {
        "" -> {
            tgMessageSender.sendChatMessage(chatId, TgTextOutput("Argument expected"), messageId)
        }
        else -> {
            val markdownText = if (value.toIntOrNull() != null) {
                if (value.toInt() in 1..1000) {
                    settings[POST_COUNT_TO_LOAD] = value
                    "Every time $value posts will be loaded from VK"
                } else {
                    "Value must be between 1 and 1000"
                }
            } else {
                "Integer value expected, got '$value'"
            }
            tgMessageSender.sendChatMessage(chatId, TgTextOutput(markdownText), messageId)
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
    putIfAbsent(RETENTION_PERIOD_DAYS, "7")
    putIfAbsent(POST_COUNT_TO_LOAD, "300")
    putIfAbsent(VK_COMMUNITY_ID, "-57536014")
    putIfAbsent(FORWARDING_ENABLED, "true")
    putIfAbsent(USE_PHOTO_MODE, "true")
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