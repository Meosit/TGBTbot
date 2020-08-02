package com.tgbt.vk

import io.ktor.client.HttpClient
import io.ktor.client.request.get

private const val VK_API_VERSION = 5.120
private const val MAX_POSTS_COUNT = 100

class VkPostLoader(private val http: HttpClient, token: String) {
    private val apiBaseUrl = "https://api.vk.com/method/wall.get?v=$VK_API_VERSION&filter=owner&access_token=$token"

    suspend fun load(totalCount: Int, communityId: Long): List<VkPost> {
        val items = ArrayList<VkPost>(totalCount)
        var offset = 0
        var remainig = totalCount

        do {
            val count = if (remainig < MAX_POSTS_COUNT) remainig else MAX_POSTS_COUNT
            val url = "$apiBaseUrl&count=$count&offset=$offset&owner_id=$communityId"
            val pageItems = http.get<VkWallGet>(url).response.items
            items.addAll(pageItems)
            offset += MAX_POSTS_COUNT
            remainig -= MAX_POSTS_COUNT
        } while (remainig >= MAX_POSTS_COUNT)

        return items
    }

}