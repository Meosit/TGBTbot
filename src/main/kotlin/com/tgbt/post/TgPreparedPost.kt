package com.tgbt.post

import com.tgbt.misc.escapeMarkdown

data class TgPreparedPost(
    val text: String,
    val maybeImage: String?,
    val footerMarkdown: String = "",
    val suggestionReference: String = "",
    val editorComment: String = ""
) {
    val formattedFooter by lazy {
        val compiled =
            (if (editorComment.isBlank()) "" else "\n———\n${editorComment.escapeMarkdown()}\n") +
                    (if (suggestionReference.isBlank()) "" else "\n${suggestionReference.escapeMarkdown()}") +
                    (if (footerMarkdown.isBlank()) "" else "\n$footerMarkdown")
        if (compiled.isBlank()) "" else "\n$compiled"
    }

    val withoutImage = text.escapeMarkdown() + formattedFooter

    val withImage = """${maybeImage?.let { "[\u200C](${it})" } ?: ""}$withoutImage"""

    val canBeSendAsImageWithCaption = maybeImage != null && withoutImage.length <= 1024

    fun imageUrl() = maybeImage!!
}