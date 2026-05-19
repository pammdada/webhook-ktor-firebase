package com.example

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object AppConfig {
    private val json = Json { ignoreUnknownKeys = true }

    val verifyToken: String by lazy {
        System.getenv("VERIFY_TOKEN") ?: "dev_token_local"
    }

    val phoneRegistry: Map<String, PhoneData> by lazy {
        val raw = System.getenv("PHONE_REGISTRY_JSON") ?: "{}"
        json.decodeFromString<Map<String, PhoneData>>(raw)
    }

    val firebaseDatabaseUrl: String? by lazy {
        System.getenv("FIREBASE_DATABASE_URL")
    }
}

@Serializable
data class PhoneData(
    val token: String,
    val name: String = ""
)
