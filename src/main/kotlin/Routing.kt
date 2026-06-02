package com.example

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

private val firebaseService = FirebaseService()
private val retryQueue = RetryQueue(firebaseService)
private val whatsAppApiService = WhatsAppApiService.create()
private val repository = MessageRepository(firebaseService, whatsAppApiService, retryQueue)

private val log = LoggerFactory.getLogger("com.example.RoutingKt")

fun Application.configureRouting() {
    retryQueue.start(this)

    routing {
        get("/") {
            call.respondText("Ktor WhatsApp Webhook Server is running!")
        }

        get("/send") {
            val destinatario = call.request.queryParameters["to"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Falta el parámetro 'to'")

            val senderId = call.request.queryParameters["from"]
            var phoneData: PhoneData? = if (senderId != null) repository.resolvePhoneData(senderId) else null
            if (phoneData == null) {
                phoneData = firebaseService.getCachedPhoneRegistry().values.firstOrNull()
            }

            if (phoneData == null) {
                return@get call.respond(HttpStatusCode.BadRequest, "No hay emisores registrados.")
            }

            val status = repository.sendResponse(destinatario, "¡Hola desde Ktor! 🚀", phoneData.phoneNumberId)
            call.respond(HttpStatusCode.OK, "Status: $status")
        }

        route("/webhook") {
            get {
                val params = call.request.queryParameters
                if (params["hub.mode"] == "subscribe" && params["hub.verify_token"] == AppConfig.verifyToken) {
                    call.respondText(params["hub.challenge"] ?: "", status = HttpStatusCode.OK)
                } else {
                    call.respond(HttpStatusCode.Forbidden)
                }
            }

            post {
                try {
                    // Deserializa automáticamente el JSON del webhook de WhatsApp usando kotlinx.serialization + ContentNegotiation
                    val payload = call.receive<WebhookPayload>()
                    // Responde inmediatamente 200 OK a Meta para evitar reintentos (Meta exige respuesta en <20s)
                    call.respond(HttpStatusCode.OK)

                    // Procesamiento asíncrono: guardar en Firebase, responder al usuario
                    launch(Dispatchers.IO) {
                        try {
                            payload.entry.forEach { entry ->
                                entry.changes.forEach { change ->
                                    // Extrae el ID del número de teléfono de negocio desde metadata
                                    val phoneNumberId = change.value.metadata.phoneNumberId
                                    // Busca token Bearer en caché local → fallback a Firebase RTDB
                                    val phoneData = repository.resolvePhoneData(phoneNumberId)

                                    if (phoneData == null) {
                                        log.warn("ID $phoneNumberId no registrado (cache + Firebase). Guardando mensaje sin respuesta.")
                                    }

                                    // Itera TODOS los mensajes del array (puede haber varios en un solo webhook)
                                    change.value.messages?.forEach { message ->
                                        // Guarda mensaje completo en Firebase y envía respuesta automática
                                        repository.processWebhookMessage(message, phoneNumberId, phoneData)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            log.error("Error processing webhook: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    log.error("Error parsing webhook: ${e.message}")
                    // Si falla el parseo, aún respondemos 200 para que Meta no reintente
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}
