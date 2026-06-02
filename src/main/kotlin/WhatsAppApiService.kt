package com.example

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

// Retrofit interface para WhatsApp Cloud API v19.0
interface WhatsAppApiService {

    // Endpoint oficial de Meta. {phoneNumberId} = ID del número de negocio
    @POST("v19.0/{phoneNumberId}/messages")
    // Token Bearer sacado de Firebase (phoneRegistry/{phoneNumberId}/token)
    suspend fun sendMessage(
        @Path("phoneNumberId") phoneNumberId: String,
        @Header("Authorization") authorization: String,
        // Body JSON: { messaging_product, recipient_type, to, type, text: { body } }
        @Body request: WhatsAppMessageRequest
    ): retrofit2.Response<WhatsAppMessageResponse>

    companion object {
        // Base URL de la API Graph de Meta
        private const val BASE_URL = "https://graph.facebook.com/"

        fun create(): WhatsAppApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .build()

            val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                // Converter de kotlinx.serialization para Retrofit
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()

            return retrofit.create(WhatsAppApiService::class.java)
        }
    }
}
