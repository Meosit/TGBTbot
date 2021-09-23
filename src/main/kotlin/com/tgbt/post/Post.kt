package com.tgbt.post

import com.tgbt.misc.moscowZoneId
import com.tgbt.vk.VkPost
import java.time.Instant
import java.time.LocalTime

data class Post(
    val id: Long,
    val unixTime: Long,
    val text: String,
    val imageUrl: String?,
    val stats: PostStats
)

fun VkPost.toPost(): Post {
    val stats = PostStats(
        likes.count,
        reposts.count,
        comments.count,
        views.count
    )
    val imageUrl = attachments
        .mapNotNull { it.photo }
        .map { photo -> photo.sizes.maxBy { it.height * it.width } }
        .firstOrNull()?.url
    return Post(id, date, text, imageUrl, stats)
}

val Post.localTime: LocalTime get() = Instant.ofEpochSecond(unixTime).atZone(moscowZoneId).toLocalTime()