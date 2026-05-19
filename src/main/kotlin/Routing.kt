package com.example

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory


val httpClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        json()
    }
}

private val firebaseService: FirebaseService? by lazy {
    runCatching { FirebaseService() }
        .onFailure { println("Firebase no disponible, modo degradado: ${it.message}") }
        .getOrNull()
}

private val retryQueue = RetryQueue(firebaseService)

private val log = LoggerFactory.getLogger("com.example.RoutingKt")

fun Application.configureRouting() {
    retryQueue.start(this)

    routing {
        get("/") {
            call.respondText("Ktor WhatsApp Webhook Server is running!")
        }

        get("/send") {
            val destinatario = call.request.queryParameters["to"]
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    "Falta el parámetro 'to'"
                )

            val senderId = call.request.queryParameters["from"]
                ?: AppConfig.phoneRegistry.keys.firstOrNull()
            val phoneData = senderId?.let { AppConfig.phoneRegistry[it] }

            if (senderId == null || phoneData == null) {
                return@get call.respond(HttpStatusCode.BadRequest, "No hay números emisores registrados.")
            }

            val status = sendWhatsAppMessage(
                destinatario, "¡Hola desde Ktor! 🚀", senderId, phoneData.token
            )
            call.respond(HttpStatusCode.OK, "Enviado con status: ${status ?: "Error Interno"}")
        }

        route("/webhook") {

            get {
                val params = call.request.queryParameters
                val mode = params["hub.mode"]
                val token = params["hub.verify_token"]
                val challenge = params["hub.challenge"]

                println("--- Intento de Verificación de Webhook ---")
                println("Modo: $mode, Token: $token")

                if (mode == "subscribe" && token == AppConfig.verifyToken) {
                    println("Verificación exitosa")
                    call.respondText(challenge ?: "", status = HttpStatusCode.OK)
                } else {
                    println("Verificación fallida")
                    call.respond(HttpStatusCode.Forbidden)
                }
            }

            post {
                val body = call.receiveText()
                val json = Json.parseToJsonElement(body).jsonObject

                val phoneNumberId = json["entry"]?.jsonArray?.getOrNull(0)?.jsonObject
                    ?.get("changes")?.jsonArray?.getOrNull(0)?.jsonObject
                    ?.get("value")?.jsonObject
                    ?.get("metadata")?.jsonObject
                    ?.get("phone_number_id")?.jsonPrimitive?.content

                println("--- Webhook POST Recibido ---")
                if (phoneNumberId != null && AppConfig.phoneRegistry.containsKey(phoneNumberId)) {
                    val phoneData = AppConfig.phoneRegistry[phoneNumberId]
                    val accessToken = phoneData?.token
                    println("Número identificado: ${phoneData?.name} ($phoneNumberId)")

                    launch {

                        //corutina,  poner dispacher io. Revisar hilos java. Abrir otro hilo. el json se lee en dispacher io, para le hilo principal dispache main
                        handleIncomingMessage(json, accessToken, phoneNumberId)
                    }

                    call.respond(HttpStatusCode.OK)
                } else {
                    println("Aviso: ID de número '$phoneNumberId' no reconocido o no registrado.")
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}

suspend fun handleIncomingMessage(
    json: JsonObject,
    accessToken: String?,
    senderId: String
) {
    val value = json["entry"]?.jsonArray?.getOrNull(0)?.jsonObject
        ?.get("changes")?.jsonArray?.getOrNull(0)?.jsonObject
        ?.get("value")?.jsonObject

    val messages = value?.get("messages")?.jsonArray
    if (messages != null && messages.isNotEmpty()) {
        val msg = messages[0].jsonObject
        val from = msg["from"]?.jsonPrimitive?.content ?: return
        val msgBody = msg["text"]?.jsonObject?.get("body")?.jsonPrimitive?.content ?: ""
        val msgId = msg["id"]?.jsonPrimitive?.content ?: "unknown_${System.currentTimeMillis()}"
        val rawTs = msg["timestamp"]?.jsonPrimitive?.content
        //revisar
        val timestamp = (rawTs?.toLongOrNull() ?: System.currentTimeMillis()) * 1000L

        println("Mensaje entrante de: $from - $msgBody")

        val incoming = IncomingMessage(
            from = from,
            to = senderId,
            body = msgBody,
            messageId = msgId,
            timestamp = timestamp
        )

        val saved = firebaseService?.let {
        //poner en corutina segundo plano.
            runCatching { it.saveIncoming(incoming) }.getOrDefault(false)
        } ?: false

        println("guardando en bdd: $saved")

        if (!saved) {
            retryQueue.enqueue(PendingIncoming(incoming))
        }

        println("previo al access token: $accessToken")

        if (accessToken != null) {
            val status = sendWhatsAppMessage(
                from, "¡Hola! Recibimos tu mensaje en nuestro servidor.", senderId, accessToken
            )

            val outgoing = OutgoingMessage(
                from = senderId,
                to = from,
                body = "¡Hola! Recibimos tu mensaje en nuestro servidor.",
                status = if (status != null) "sent" else "failed",
                statusCode = status?.value,
                timestamp = System.currentTimeMillis()
            )

            val savedOut = firebaseService?.let {
                runCatching { it.saveOutgoing(outgoing) }.getOrDefault(false)
            } ?: false

            if (!savedOut) {
                retryQueue.enqueue(PendingOutgoing(outgoing))
            }
        }
    }
}

suspend fun sendWhatsAppMessage(
    to: String,
    text: String,
    phoneNumberId: String,
    token: String
): HttpStatusCode? {
    println("Enviando respuesta a $to a través de $phoneNumberId...")
    return try {
        //No usar la libreria httclient, usar retroFit, usar callback,

        val response: HttpResponse = httpClient.post(
            "https://graph.facebook.com/v19.0/$phoneNumberId/messages"
        ) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("messaging_product", "whatsapp")
                put("to", to)
                put("type", "text")
                put("text", buildJsonObject {
                    put("body", text)
                })
            })
        }
        if (response.status == HttpStatusCode.OK) {
            println("Respuesta enviada con éxito. Estado: ${response.status}")
        } else {
            val errorBody = response.bodyAsText()
            println("Error al enviar mensaje. Estado: ${response.status}, Detalle: $errorBody")
        }
        response.status
    } catch (e: Exception) {
        println("Error crítico enviando mensaje: ${e.message}")
        null
    }
}