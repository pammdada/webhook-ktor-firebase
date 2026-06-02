package com.example.webhook

import com.example.toFirebaseValue
import kotlinx.serialization.json.*

// Convierte el payload completo del webhook (WebhookPayload) a Map<String, Any?> para Firebase RTDB
// Usa kotlinx.serialization para preservar todos los campos del JSON original de Meta
fun WebhookPayload.toFirebaseMap(): Map<String, Any?> {
    val jsonObj = Json.encodeToJsonElement(WebhookPayload.serializer(), this).jsonObject
    return jsonObj.mapValues { it.value.toFirebaseValue() }
}

// Convierte un mensaje individual (WebhookMessage) a Map<String, Any?> para guardar en conversations/{from}/messages/
// Mantiene la estructura nativa de Meta (from, id, timestamp, type, text, image, audio, etc.)
fun WebhookMessage.toFirebaseMap(): Map<String, Any?> {
    val jsonObj = Json.encodeToJsonElement(WebhookMessage.serializer(), this).jsonObject
    return jsonObj.mapValues { it.value.toFirebaseValue() }
}
