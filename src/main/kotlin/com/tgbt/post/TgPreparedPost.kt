package com.tgbt.post

import com.tgbt.misc.escapeMarkdown

data class TgPreparedPost(
    val text: String,
    val maybeImage: String?,
    val footerMarkdown: String = ""
) {
    val withoutImage = text.escapeMarkdown() + if (footerMarkdown.isBlank()) "" else "\n\n$footerMarkdown"

    val withImage = """${maybeImage?.let { "[\u200C](${it})" } ?: ""}$withoutImage"""

    val canBeSendAsImageWithCaption = maybeImage != null && withoutImage.length <= 1024

    fun imageUrl() = maybeImage!!
}