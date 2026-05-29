package com.example

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object AppConfig {
    private val json = Json { ignoreUnknownKeys = true }

    val verifyToken: String by lazy {
        System.getenv("VERIFY_TOKEN") ?: "dev_token_local"
    }

    val accessToken: String by lazy {
        System.getenv("ACCESS_TOKEN") ?: "dev_access_token_local"
    }

    val firebaseDatabaseUrl: String? by lazy {
        System.getenv("FIREBASE_DATABASE_URL")
    }
}
