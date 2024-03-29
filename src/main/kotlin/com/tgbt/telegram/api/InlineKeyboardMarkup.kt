package com.tgbt.telegram.api

import com.tgbt.BotJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class InlineKeyboardMarkup(
    @SerialName("inline_keyboard") val inlineKeyboard: List<List<InlineKeyboardButton>>
)

@Serializable
data class InlineKeyboardButton(
    val text: String,
    @SerialName("callback_data") val callbackData: String? = null
)

fun InlineKeyboardButton.toMarkup() = InlineKeyboardMarkup(listOf(listOf(this)))


fun InlineKeyboardMarkup.toJson() = BotJson.encodeToString(InlineKeyboardMarkup.serializer(), this)