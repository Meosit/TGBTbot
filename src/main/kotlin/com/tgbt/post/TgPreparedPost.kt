package com.tgbt.post

import com.tgbt.BotJson
import com.tgbt.BotOwnerIds
import com.tgbt.misc.escapeMarkdown
import com.tgbt.misc.isImageUrl
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.TelegraphPostCreator
import com.tgbt.telegram.api.InlineKeyboardMarkup
import com.tgbt.telegram.api.Message
import com.tgbt.telegram.output.TgImageOutput
import com.tgbt.telegram.output.TgTextOutput
import org.slf4j.LoggerFactory

data class TgPreparedPost(
    val text: String,
    val maybeImage: String?,
    val footerMarkdown: String = "",
    val authorSign: String = "",
    val editorComment: String = "",
) {
    private val formattedFooter by lazy {
        val compiled =
            (if (editorComment.isBlank()) "" else "\n———\n${editorComment.escapeMarkdown()}\n") +
                    (if (authorSign.isBlank()) "" else "\n${authorSign.escapeMarkdown()}") +
                    (if (footerMarkdown.isBlank()) "" else "\n$footerMarkdown")
        if (compiled.isBlank()) "" else "\n$compiled"
    }

    val withoutImage = text.escapeMarkdown() + formattedFooter

    val withImage = """${maybeImage?.let { "[\u200C](${it})" } ?: ""}$withoutImage"""

    private val canBeSendAsImageWithCaption = maybeImage != null && withoutImage.length <= MAX_IMAGE_POST_SIZE

    companion object {
        private val logger = LoggerFactory.getLogger("TgPreparedPost")
        const val MAX_TEXT_POST_SIZE = 4096
        const val MAX_IMAGE_POST_SIZE = 1024
    }

    suspend fun sendTo(
        targetChat: String,
        keyboardMarkup: InlineKeyboardMarkup? = null
    ): Message? {
        val keyboardJson = keyboardMarkup?.let { BotJson.encodeToString(InlineKeyboardMarkup.serializer(), keyboardMarkup) }
        return when {
            canBeSendAsImageWithCaption -> TelegramClient
                .sendChatPhoto(
                    targetChat,
                    TgImageOutput(withoutImage, maybeImage!!, keyboardJson)
                ).result

            withImage.length > MAX_TEXT_POST_SIZE -> {
                val (ok, error, result) = TelegraphPostCreator.createPost(this)
                when {
                    ok && result != null -> {
                        val output = TgTextOutput(
                            "Слишком длиннобугурт, поэтому читайте в телеграфе: [${result.title}](${result.url})${formattedFooter}",
                            keyboardJson
                        )
                        TelegramClient.sendChatMessage(targetChat, output, disableLinkPreview = false).result
                    }

                    else -> {
                        val message = "Failed to create Telegraph post, please check logs, error message:\n`${error}`"
                        logger.error(message)
                        val output = TgTextOutput(message)
                        BotOwnerIds.forEach { id -> TelegramClient.sendChatMessage(id, output) }
                        null
                    }
                }
            }

            else -> {
                // footer links should not be previewed.
                val disableLinkPreview = footerMarkdown.contains("https://")
                        && !text.contains("https://")
                        && !(maybeImage?.isImageUrl() ?: false)
                TelegramClient.sendChatMessage(
                    targetChat,
                    TgTextOutput(withImage, keyboardJson),
                    disableLinkPreview = disableLinkPreview
                ).result
            }
        }
    }

}