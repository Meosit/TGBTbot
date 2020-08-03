package com.tgbt.vk

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VkPost(
    val id: Long,
    val date: Long,
    @SerialName("post_type") val postType: String,
    @SerialName("is_pinned") val isPinned: Int,
    @SerialName("marked_as_ads") val markedAsAds: Int,
    val text: String = "",
    val attachments: List<VkPostAttachment> = emptyList(),
    val likes: VkPostStat,
    val comments: VkPostStat,
    val reposts: VkPostStat,
    val views: VkPostStat
)