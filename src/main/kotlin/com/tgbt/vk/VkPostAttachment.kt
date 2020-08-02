package com.tgbt.vk

import kotlinx.serialization.Serializable

@Serializable
data class VkPostAttachment(
    val photo: VkAttachmentPhoto? = null
)

@Serializable
data class VkAttachmentPhoto(
    val sizes: List<VkAttachmentPhotoSize>
)

@Serializable
data class VkAttachmentPhotoSize(
    val height: Int,
    val url: String,
    val type: String,
    val width: Int
)