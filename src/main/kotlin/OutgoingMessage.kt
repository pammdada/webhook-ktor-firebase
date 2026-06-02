package com.example

import kotlinx.serialization.Serializable

@Serializable
data class OutgoingMessage(
    val from: String,
    val to: String,
    val body: String,
    val status: String,
    val statusCode: Int? = null,
    val timestamp: Long,
    val direction: String = "outgoing"
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "from" to from,
        "to" to to,
        "body" to body,
        "status" to status,
        "statusCode" to statusCode,
        "timestamp" to timestamp,
        "direction" to direction
    )
}
