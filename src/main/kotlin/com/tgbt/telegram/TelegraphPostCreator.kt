package com.tgbt.telegram

import com.tgbt.misc.trimToLength
import com.tgbt.post.TgPreparedPost
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.features.ClientRequestException
import io.ktor.client.features.ServerResponseException
import io.ktor.client.request.post
import io.ktor.client.request.url
import io.ktor.client.statement.readText
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class TelegraphPostCreator(private val httpClient: HttpClient, private val json: Json, private val apiToken: String) {

    private val apiUrl = "https://api.telegra.ph/createPage"

    suspend fun createPost(post: TgPreparedPost): TelegraphCreateResult {

        val title = json.stringify(String.serializer(), post.withoutImage.lineSequence().first().trimToLength(256, "…"))
        val encodedText = json.stringify(String.serializer(), post.text)
        val content = if (post.maybeImage != null) {
            """[$encodedText,{"tag":"img","attrs":{"src":"${post.maybeImage}"}}]"""
        } else {
            """[$encodedText]"""
        }
        return try {
            httpClient.post {
                url("$apiUrl/createPost")
                body = TextContent("""
                    {
                        "title": $title,
                        "access_token": "$apiToken",
                        "content": $content
                    }
                    """.trimIndent(),
                    ContentType.Application.Json
                )
            }
        } catch (e: ClientRequestException) {
            e.response.receive()
        } catch (e: ServerResponseException) {
            TelegraphCreateResult(false, error = e.response.readText())
        }
    }

}