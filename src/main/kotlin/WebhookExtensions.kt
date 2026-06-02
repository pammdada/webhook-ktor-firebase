package com.example

import kotlinx.serialization.json.*

// Serializa WebhookMessage a JSON, luego convierte JsonElement → Map para Firebase RTDB
fun WebhookMessage.toFirebaseMap(): Map<String, Any?> {
    // kotlinx.serialization: data class → JsonObject
    val jsonObj = Json.encodeToJsonElement(WebhookMessage.serializer(), this).jsonObject
    return jsonObj.mapValues { it.value.toFirebaseValue() }
}

// Preserva tipos nativos: boolean, long, double, string, null
fun JsonElement.toFirebaseValue(): Any? = when (this) {
    is JsonObject -> this.mapValues { it.value.toFirebaseValue() }
    is JsonArray -> this.map { it.toFirebaseValue() }
    is JsonPrimitive -> this.booleanOrNull ?: this.longOrNull ?: this.doubleOrNull ?: this.content
    JsonNull -> null
}
