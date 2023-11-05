package com.tgbt.telegram.api

import com.tgbt.misc.escapeMarkdown
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Chat(
    val id: Long,
    val type: String,
    val title: String? = null,
    val username: String? = null,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    val description: String? = null
)

val Chat.verboseUserName: String get() =
    username?.let { "@$it" } ?: firstName?.let { "[${firstName.escapeMarkdown()}${lastName?.let { " ${it.escapeMarkdown()}" } ?: ""}](tg://user?id=$id)" } ?: "$title (Group)"

val Chat.isPrivate: Boolean get() = type == "private"
val Chat.isGroup: Boolean get() = type == "group" || type == "supergroup"