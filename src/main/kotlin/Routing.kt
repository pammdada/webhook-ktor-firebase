package com.example

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
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
                val body = call.receiveText()
                call.respond(HttpStatusCode.OK)

                launch(Dispatchers.IO) {
                    try {
                        val json = Json.parseToJsonElement(body).jsonObject
                        val phoneNumberId = json.extractWebhookValue()
                            ?.get("metadata")?.jsonObject
                            ?.get("phone_number_id")?.jsonPrimitive?.content

                        if (phoneNumberId != null) {
                            val phoneData = repository.resolvePhoneData(phoneNumberId)
                            if (phoneData == null) {
                                log.warn("ID $phoneNumberId no registrado (cache + Firebase). Guardando mensaje sin respuesta.")
                            }
                            repository.processWebhook(json, phoneNumberId, phoneData)
                        }
                    } catch (e: Exception) {
                        log.error("Error en segundo plano: ${e.message}")
                    }
                }
            }
        }
    }
}
