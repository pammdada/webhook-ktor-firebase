package com.example.whatsapp

import kotlinx.serialization.Serializable

@Serializable
data class WhatsAppMessageRequest(
    val messaging_product: String = "whatsapp",
    val recipient_type: String = "individual",
    val to: String,
    val type: String = "text",
    val text: TextBody
)

@Serializable
data class TextBody(
    val body: String
)

@Serializable
data class WhatsAppMessageResponse(
    val messaging_product: String? = null,
    val contacts: List<Contact>? = null,
    val messages: List<Message>? = null
)

@Serializable
data class Contact(
    val wa_id: String? = null
)

@Serializable
data class Message(
    val id: String? = null
)

@Serializable
data class PhoneData(
    val token: String = "",
    val name: String = "",
    val phoneNumberId: String = ""
)
