package com.example

import kotlinx.serialization.Serializable

@Serializable
data class IncomingMessage(
    val from: String,
    val to: String,
    val body: String,
    val messageId: String,
    val timestamp: Long,
    val type: String = "text"
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "from" to from,
        "to" to to,
        "body" to body,
        "messageId" to messageId,
        "timestamp" to timestamp,
        "type" to type
    )
}

@Serializable
data class OutgoingMessage(
    val from: String,
    val to: String,
    val body: String,
    val status: String,
    val statusCode: Int? = null,
    val timestamp: Long
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "from" to from,
        "to" to to,
        "body" to body,
        "status" to status,
        "statusCode" to statusCode,
        "timestamp" to timestamp
    )
}
