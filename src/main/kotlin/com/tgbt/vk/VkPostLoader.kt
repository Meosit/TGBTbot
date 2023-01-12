package com.tgbt.vk

import com.tgbt.BotHttpClient
import com.tgbt.VkToken
import io.ktor.client.call.*
import io.ktor.client.request.*
import org.slf4j.LoggerFactory

private const val VK_API_VERSION = "5.122"
private const val MAX_POSTS_COUNT = 100

private val logger = LoggerFactory.getLogger(VkPostLoader::class.java)

object VkPostLoader {
    private val apiBaseUrl = "https://api.vk.com/method/wall.get?v=$VK_API_VERSION&filter=owner&access_token=$VkToken"

    suspend fun load(totalCount: Int, communityId: Long): List<VkPost> {
        val items = ArrayList<VkPost>(totalCount)
        var offset = 0
        var remainig = totalCount

        do {
            val count = if (remainig < MAX_POSTS_COUNT) remainig else MAX_POSTS_COUNT
            val url = "$apiBaseUrl&count=$count&offset=$offset&owner_id=$communityId"
            logger.info("Fetching from $url")
            val pageItems = BotHttpClient.get(url).body<VkWallGet>().response.items
            items.addAll(pageItems)
            offset += MAX_POSTS_COUNT
            remainig -= MAX_POSTS_COUNT
        } while (remainig >= MAX_POSTS_COUNT)

        return items
    }

}