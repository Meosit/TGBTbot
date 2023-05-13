package com.tgbt.telegram

import com.tgbt.misc.trimToLength
import com.tgbt.post.TgPreparedPost
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class TelegraphPostCreator(private val httpClient: HttpClient, private val json: Json, private val apiToken: String) {

    private val apiUrl = "https://api.telegra.ph/createPage"

    suspend fun createPost(post: TgPreparedPost): TelegraphCreateResult {

        val title = json.encodeToString(String.serializer(), post.withoutImage.lineSequence().first().trimToLength(256, "…"))
        val encodedText = json.encodeToString(String.serializer(), post.text)
        val content = if (post.maybeImage != null) {
            """[$encodedText,{"tag":"img","attrs":{"src":"${post.maybeImage}"}}]"""
        } else {
            """[$encodedText]"""
        }
        return try {
            httpClient.post {
                url("$apiUrl/createPost")
                setBody(TextContent("""
                    {
                        "title": $title,
                        "access_token": "$apiToken",
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