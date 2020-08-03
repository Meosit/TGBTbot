package com.tgbt.telegram.output

import com.tgbt.misc.escapeMarkdown
import com.tgbt.misc.trimToLength
import com.tgbt.post.Post

data class TgLongPostOutput(val post: Post, val footerMd: String) : TgMessageOutput {

    override fun markdown() =
        """${post.imageUrl?.let { "[\u200C](${it})" } ?: ""}${post.text.escapeMarkdown()}${if (footerMd.isBlank()) "" else "\n\n${footerMd}"}""".trimMargin()
            .trimToLength(4096, "…")

}

