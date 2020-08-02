package com.tgbt.telegram.output

import com.tgbt.misc.trimToLength

data class TgTextOutput(
    val markdownText: String,
    val keyboardJson: String? = null
) : TgMessageOutput {

    override fun markdown(): String = markdownText.trimToLength(4096, "…")

    override fun keyboardJson() = keyboardJson
}