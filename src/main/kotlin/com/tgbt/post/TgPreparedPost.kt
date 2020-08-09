package com.tgbt.post

import com.tgbt.misc.escapeMarkdown

data class TgPreparedPost(
    val text: String,
    val maybeImage: String?,
    val footedMarkdown: String = ""
) {
    val withoutImage = text.escapeMarkdown() + if (footedMarkdown.isBlank()) "" else "\n\n${footedMarkdown}"

    val withImage = """${maybeImage?.let { "[\u200C](${it})" } ?: ""}$withoutImage"""

    val canBeSendAsImageWithCaption = maybeImage != null && withoutImage.length <= 1024

    fun imageUrl() = maybeImage!!
}