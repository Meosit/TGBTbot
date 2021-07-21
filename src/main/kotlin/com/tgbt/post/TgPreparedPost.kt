package com.tgbt.post

import com.tgbt.misc.escapeMarkdown

data class TgPreparedPost(
    val text: String,
    val maybeImage: String?,
    val footerMarkdown: String = "",
    val suggestionReference: String = ""
) {
    val formattedFooter by lazy {
        val referenceAndFooter =
            if (suggestionReference.isBlank()) "" else "\n$suggestionReference" + if (footerMarkdown.isBlank()) "" else "\n$footerMarkdown"
        if (referenceAndFooter.isBlank()) "" else "\n$referenceAndFooter"
    }

    val withoutImage = text.escapeMarkdown() + formattedFooter

    val withImage = """${maybeImage?.let { "[\u200C](${it})" } ?: ""}$withoutImage"""

    val canBeSendAsImageWithCaption = maybeImage != null && withoutImage.length <= 1024

    fun imageUrl() = maybeImage!!
}