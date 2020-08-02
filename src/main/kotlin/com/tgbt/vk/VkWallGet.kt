package com.tgbt.vk

import kotlinx.serialization.Serializable

@Serializable
data class VkWallGet(
    val response: VkWallGetResponse
)

@Serializable
data class VkWallGetResponse(
    val items: List<VkPost>
)