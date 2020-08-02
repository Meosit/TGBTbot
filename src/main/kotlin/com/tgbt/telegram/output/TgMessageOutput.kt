package com.tgbt.telegram.output

interface TgMessageOutput {
    fun markdown(): String
    fun keyboardJson(): String? = null
}