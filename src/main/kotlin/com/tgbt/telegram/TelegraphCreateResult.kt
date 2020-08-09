package com.tgbt.telegram

import kotlinx.serialization.Serializable

@Serializable
data class TelegraphCreateResult(
    val ok: Boolean,
    val error: String? = null,
    val result: TelegraphPostInfo? = null
)

@Serializable
data class TelegraphPostInfo(
    val path: String,
    val url: String,
    val title: String
)
