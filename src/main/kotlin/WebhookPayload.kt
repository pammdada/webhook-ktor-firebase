package com.example

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
// Raíz del JSON que envía Meta en el webhook
data class WebhookPayload(
    // "object" es keyword de Kotlin, por eso se mapea a "obj"
    @SerialName("object") val obj: String,
    val entry: List<WebhookEntry>
)

@Serializable
data class WebhookEntry(
    val id: String,
    val changes: List<WebhookChange>
)

@Serializable
data class WebhookChange(
    val value: WebhookValue,
    val field: String
)

@Serializable
// Contiene metadata, contacts, messages[] y statuses[]
data class WebhookValue(
    @SerialName("messaging_product") val messagingProduct: String,
    val metadata: WebhookMetadata,
    val contacts: List<WebhookContact>? = null,
    val messages: List<WebhookMessage>? = null,
    val statuses: List<WebhookStatus>? = null
)

@Serializable
data class WebhookMetadata(
    // display_phone_number = número visible, phone_number_id = ID del negocio
    @SerialName("display_phone_number") val displayPhoneNumber: String,
    @SerialName("phone_number_id") val phoneNumberId: String
)

@Serializable
data class WebhookContact(
    val profile: WebhookProfile,
    @SerialName("wa_id") val waId: String
)

@Serializable
data class WebhookProfile(
    val name: String
)
