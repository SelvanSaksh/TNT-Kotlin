package network.models

import kotlinx.serialization.Serializable

@Serializable
data class ApiErrorResponse(
    val status: Boolean,
    val statusCode: Int,
    val message: String,
    val timestamp: String? = null,
    val path: String? = null
)
