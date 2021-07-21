package com.tgbt.telegram.output

import com.tgbt.misc.trimToLength

data class TgImageOutput(
    val markdownText: String,
    val imageUrl: String,
    val keyboardJson: String? = null
) : TgMessageOutput {

    override fun markdown() = markdownText.trimToLength(1024, "…")

    fun imageUrl() = imageUrl

    override fun keyboardJson(): String? = keyboardJson
}