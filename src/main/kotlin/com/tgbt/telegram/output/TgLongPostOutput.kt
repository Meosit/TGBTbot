package com.tgbt.telegram.output

import com.tgbt.misc.escapeMarkdown
import com.tgbt.misc.trimToLength
import com.tgbt.post.Post

data class TgLongPostOutput(val post: Post) : TgMessageOutput {

    override fun markdown() = """${post.imageUrl?.let { "[\u200C](${it})" } ?: ""}${post.text.escapeMarkdown()}"""
        .trimToLength(4096, "…")

}

