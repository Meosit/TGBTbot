package com.tgbt.bot

import com.tgbt.post.PostStore
import com.tgbt.settings.Settings
import com.tgbt.suggestion.SuggestionStore
import com.tgbt.telegram.TelegraphPostCreator
import com.tgbt.telegram.TgMessageSender
import com.tgbt.vk.VkPostLoader
import kotlinx.serialization.json.Json

data class BotContext(
    val json: Json,
    val ownerIds: List<String>,
    val postStore: PostStore,
    val suggestionStore: SuggestionStore,
    val settings: Settings,
    val tgMessageSender: TgMessageSender,
    val telegraphPostCreator: TelegraphPostCreator,
    val vkPostLoader: VkPostLoader
)