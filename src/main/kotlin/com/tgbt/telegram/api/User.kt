package com.tgbt.telegram.api

import com.tgbt.misc.escapeMarkdown
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: Long,
    @SerialName("is_bot") val isBot: Boolean,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String? = null,
    val username: String? = null,
    @SerialName("language_code") val languageCode: String? = null
)

val User.verboseUserName: String get() =
    username?.let { "@$it" } ?: "[${firstName.escapeMarkdown()}${lastName?.escapeMarkdown()?.let { " $it" } ?: ""}](tg://user?id=$id)"

val User?.simpleRef: String get() = if (this != null) username?.let { "@$it" } ?: firstName.escapeMarkdown() else "???"