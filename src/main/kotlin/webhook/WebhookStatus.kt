package com.example.webhook

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WebhookStatus(
    val id: String,
    @SerialName("recipient_id") val recipientId: String? = null,
    val status: String,
    val timestamp: String,
    val conversation: WebhookConversation? = null,
    val pricing: WebhookPricing? = null,
    val errors: List<WebhookStatusError>? = null
)

@Serializable
data class WebhookConversation(
    val id: String,
    val origin: WebhookConversationOrigin? = null,
    @SerialName("expiration_timestamp") val expirationTimestamp: String? = null,
    val category: String? = null,
    @SerialName("is_billable") val isBillable: Boolean? = null
)

@Serializable
data class WebhookConversationOrigin(
    val type: String
)

@Serializable
data class WebhookPricing(
    @SerialName("billable") val billable: Boolean? = null,
    @SerialName("pricing_model") val pricingModel: String? = null,
    val category: String? = null
)

@Serializable
data class WebhookStatusError(
    val code: Int,
    val title: String
)
