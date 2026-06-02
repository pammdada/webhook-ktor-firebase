package com.example.whatsapp

import com.example.toFirebaseValue
import kotlinx.serialization.json.*

// Convierte la request saliente (WhatsAppMessageRequest) a Map<String, Any?> para guardar en conversations/{to}/outgoingRequests/
// Formato exacto que se envía a Meta: messaging_product, recipient_type, to, type, text.body
fun WhatsAppMessageRequest.toFirebaseMap(): Map<String, Any?> {
    val jsonObj = Json.encodeToJsonElement(WhatsAppMessageRequest.serializer(), this).jsonObject
    return jsonObj.mapValues { it.value.toFirebaseValue() }
}
