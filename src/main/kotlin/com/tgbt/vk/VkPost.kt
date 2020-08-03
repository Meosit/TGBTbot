package com.tgbt.vk

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VkPost(
    val id: Long,
    val date: Long,
    @SerialName("post_type") val postType: String = "post",
    @SerialName("is_pinned") val isPinned: Int = 0,
    @SerialName("marked_as_ads") val markedAsAds: Int = 0,
    val text: String = "",
    val attachments: List<VkPostAttachment> = emptyList(),
    val likes: VkPostStat = VkPostStat(0),
    val comments: VkPostStat = VkPostStat(0),
    val reposts: VkPostStat = VkPostStat(0),
    val views: VkPostStat = VkPostStat(0)
)