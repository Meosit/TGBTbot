package com.tgbt.telegram.output

import com.tgbt.misc.trimToLength

data class TgImageOutput(
    val markdownText: String,
    val imageUrl: String
) : TgMessageOutput {

    override fun markdown() = markdownText.trimToLength(1024, "…")

    fun imageUrl() = imageUrl
}