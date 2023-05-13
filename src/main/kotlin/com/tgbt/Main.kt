package com.tgbt

import com.tgbt.bot.MessageContext
import com.tgbt.bot.editor.button.MainMenuHandler
import com.tgbt.bot.owner.ForcePublishSuggestionsCommand
import com.tgbt.bot.owner.ForceVKForwardCommand
import com.tgbt.grammar.exprModule
import com.tgbt.misc.launchScheduledRoutine
import com.tgbt.misc.trimToLength
import com.tgbt.settings.Setting.CHECK_PERIOD_MINUTES
import com.tgbt.settings.Setting.SUGGESTION_POLLING_DELAY_MINUTES
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.api.Update
import com.tgbt.telegram.api.anyText
import com.tgbt.telegram.api.simpleRef
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.plus
import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.io.StringWriter

private val logger = LoggerFactory.getLogger("MainKt")

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
            call.respond(HttpStatusCode.OK)
            try {
                val update = call.receive<Update>()
                val msg = update.message ?: update.editedMessage
                when {
                    msg != null -> {
                        val msgContext = MessageContext(msg, isEdit = update.editedMessage != null)
                        msgContext.handleUpdate()
                    }

                    update.callbackQuery?.message != null -> {
                        logger.info(
                            "Callback (${update.callbackQuery.from.simpleRef})${update.callbackQuery.data} to ${
                                update.callbackQuery.message.anyText?.trimToLength(
                                    50
                                )
                            }"
                        )
                        val notificationText = MainMenuHandler.handle(
                            update.callbackQuery.message,
                            update.callbackQuery.from.simpleRef,
                            update.callbackQuery.data
                        )
                        TelegramClient.pingCallbackQuery(update.callbackQuery.id, notificationText)
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
        }
        get("/") {
            call.request
            call.respondText("What are you looking here?", ContentType.Text.Html)
        }
    }

    launchScheduledRoutine(CHECK_PERIOD_MINUTES, "VK Post Forwarding", ForceVKForwardCommand::forwardVkPosts)
    launchScheduledRoutine(
        SUGGESTION_POLLING_DELAY_MINUTES,
        "Suggestions Publishing",
        ForcePublishSuggestionsCommand::forwardSuggestions
    )
}