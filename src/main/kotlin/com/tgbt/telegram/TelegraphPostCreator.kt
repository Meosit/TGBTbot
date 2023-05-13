package com.tgbt.telegram

import com.tgbt.BotHttpClient
import com.tgbt.BotJson
import com.tgbt.TelegraphToken
import com.tgbt.misc.teaserString
import com.tgbt.post.TgPreparedPost
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.serialization.builtins.serializer

object TelegraphPostCreator {

    private const val apiUrl = "https://api.telegra.ph/createPage"

    suspend fun createPost(post: TgPreparedPost): TelegraphCreateResult {

        val title = BotJson.encodeToString(String.serializer(), post.withoutImage.lineSequence().first().teaserString(256))
        val encodedText = BotJson.encodeToString(String.serializer(), post.text)
        val content = if (post.maybeImage != null) {
            """[$encodedText,{"tag":"img","attrs":{"src":"${post.maybeImage}"}}]"""
        } else {
            """[$encodedText]"""
        }
        return try {
            BotHttpClient.post {
                url("$apiUrl/createPost")
                setBody(TextContent("""
                    {
                        "title": $title,
                        "access_token": "$TelegraphToken",
                        "content": $content
                    }
                    """.trimIndent(),
                    ContentType.Application.Json
                ))
            }.body()
        } catch (e: ClientRequestException) {
            e.response.body()
        } catch (e: ServerResponseException) {
            TelegraphCreateResult(false, error = e.response.bodyAsText())
        }
    }

}