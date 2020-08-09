package com.tgbt.telegram

import com.tgbt.misc.trimToLength
import com.tgbt.post.TgPreparedPost
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.features.ClientRequestException
import io.ktor.client.features.ServerResponseException
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.url
import io.ktor.client.statement.readText
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class TelegraphPostCreator(private val httpClient: HttpClient, private val json: Json, private val apiToken: String) {

    private val apiUrl = "https://api.telegra.ph/createPage"

    suspend fun createPost(post: TgPreparedPost): TelegraphCreateResult {

        val title = post.withoutImage.lineSequence().first().trimToLength(256, "…")
        val encodedText = json.stringify(String.serializer(), post.withoutImage)
        val content = if (post.maybeImage != null) {
            """[$encodedText,{"tag":"img","attrs":{"src":"${post.maybeImage}"}}]"""
        } else {
            """[$encodedText]"""
        }
        return try {
            httpClient.post {
                url("$apiUrl/createPost")
                parameter("title", title)
                parameter("access_token", apiToken)
                parameter("content", content)
            }
        } catch (e: ClientRequestException) {
            e.response.receive()
        } catch (e: ServerResponseException) {
            TelegraphCreateResult(false, error = e.response.readText())
        }
    }

}