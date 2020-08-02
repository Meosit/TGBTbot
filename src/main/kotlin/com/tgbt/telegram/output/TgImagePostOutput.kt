package com.tgbt.telegram.output

import com.tgbt.misc.escapeMarkdown
import com.tgbt.misc.trimToLength
import com.tgbt.post.Post

data class TgImagePostOutput(val post: Post) : TgMessageOutput {

    init {
        requireNotNull(post.imageUrl)
    }

    override fun markdown() = post.text.escapeMarkdown().trimToLength(1024, "…")

    fun imageUrl() = post.imageUrl!!

}

