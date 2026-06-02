package com.example

import com.example.service.FirebaseService
import com.example.service.MessageRepository
import com.example.service.RetryQueue
import com.example.webhook.WebhookPayload
import com.example.whatsapp.PhoneData
import com.example.whatsapp.WhatsAppApiService
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
                    // 1. Recibe el payload JSON de Meta y lo deserializa en WebhookPayload (kotlinx.serialization)
                    val payload = call.receive<WebhookPayload>()
                    log.info("Webhook POST recibido: object=${payload.obj}, entries=${payload.entry.size}")

                    // 2. Responde 200 OK inmediatamente para que Meta no reintente (timeout < 20s)
                    call.respond(HttpStatusCode.OK)

                    // 3. Procesamiento asíncrono en background: guardar raw + mensajes + responder
                    launch(Dispatchers.IO) {
                        try {
                            repository.processWebhookPayload(payload)
                        } catch (e: Exception) {
                            log.error("Error processing webhook: ${e.message}", e)
                        }
                    }
                } catch (e: Exception) {
                    log.error("Error parsing webhook JSON: ${e.message}")
                    // Si falla el parseo, igual respondemos 200 para evitar reintentos de Meta
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}
