package com.example

import kotlinx.serialization.json.*

fun JsonElement.toFirebaseValue(): Any? = when (this) {
    is JsonObject -> this.mapValues { it.value.toFirebaseValue() }
    is JsonArray -> this.map { it.toFirebaseValue() }
    is JsonPrimitive -> this.booleanOrNull ?: this.longOrNull ?: this.doubleOrNull ?: this.content
    JsonNull -> null
}
