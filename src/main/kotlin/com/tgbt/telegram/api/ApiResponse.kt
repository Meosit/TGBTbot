package com.tgbt.telegram.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse(
    val ok: Boolean,
    val result: Message? = null,
    @SerialName("error_code") val errorCode: Int? = null,
    val description: String? = null,
    val parameters: ApiResponseParameters? = null
)

@Serializable
data class ApiResponseParameters(
    @SerialName("retry_after") val retryAfter: Long? = null
)

