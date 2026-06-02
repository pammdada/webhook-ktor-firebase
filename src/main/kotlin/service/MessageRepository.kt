package com.example.service

import com.example.OutgoingMessage
import com.example.webhook.WebhookMessage
import com.example.webhook.WebhookPayload
import com.example.webhook.toFirebaseMap
import com.example.whatsapp.PhoneData
import com.example.whatsapp.TextBody
import com.example.whatsapp.WhatsAppApiService
import com.example.whatsapp.WhatsAppMessageRequest
import com.example.whatsapp.toFirebaseMap
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class MessageRepository(
    private val firebaseService: FirebaseService,
    private val whatsAppApiService: WhatsAppApiService,
    private val retryQueue: RetryQueue
) {
    private val log = LoggerFactory.getLogger(MessageRepository::class.java)

    fun getPhoneData(phoneNumberId: String): PhoneData? {
        return firebaseService.getCachedPhoneRegistry()[phoneNumberId]
    }

    suspend fun resolvePhoneData(phoneNumberId: String): PhoneData? {
        return getPhoneData(phoneNumberId) ?: firebaseService.getPhoneDataDirectly(phoneNumberId)
    }

    // Recibe el payload completo del webhook de Meta (object + entry[] + changes[] + value + messages[])
    // Lo guarda completo (raw) y luego procesa cada mensaje individual
    suspend fun processWebhookPayload(payload: WebhookPayload) {
        log.info("Webhook recibido: object=${payload.obj}, entries=${payload.entry.size}")

        // Guarda el payload completo tal cual llegó de Meta en rawWebhookPayloads/<pushId>/
        val rawPayloadMap = payload.toFirebaseMap()
        firebaseService.saveRawWebhookPayload(rawPayloadMap)

        payload.entry.forEach { entry ->
            entry.changes.forEach { change ->
                val phoneNumberId = change.value.metadata.phoneNumberId
                val phoneData = resolvePhoneData(phoneNumberId)
                if (phoneData == null) {
                    log.warn("ID $phoneNumberId no registrado (cache + Firebase). Guardando mensaje sin respuesta.")
                }
                val messageCount = change.value.messages?.size ?: 0
                log.info("Procesando change: phoneNumberId=$phoneNumberId, mensajes=$messageCount")
                change.value.messages?.forEach { message ->
                    processWebhookMessage(message, phoneNumberId, phoneData)
                }
            }
        }
    }

    // Guarda el mensaje individual (WebhookMessage) en conversations/{from}/messages/<pushId>/
    // Luego responde automáticamente si tenemos token para phoneNumberId
    private suspend fun processWebhookMessage(message: WebhookMessage, phoneNumberId: String, phoneData: PhoneData?) {
        log.info("Procesando mensaje: id=${message.id}, type=${message.type}, from=${message.from}")

        val messageMap = message.toFirebaseMap()
        val saved = firebaseService.saveWebhookMessage(message.from, messageMap)
        if (!saved) retryQueue.enqueue(PendingWebhookMessage(message.from, messageMap))

        if (phoneData != null) {
            log.info("Enviando respuesta automática a ${message.from} vía $phoneNumberId")
            sendResponse(message.from, "¡Hola! Recibimos tu mensaje en nuestro servidor.", phoneNumberId)
        }
    }

    // Construye la request exacta que Meta espera: { messaging_product, recipient_type, to, type, text: { body } }
    // La guarda en conversations/{to}/outgoingRequests/<pushId>/ y luego la envía por POST a WhatsApp Cloud API
    suspend fun sendResponse(to: String, text: String, phoneNumberId: String): Int? {
        log.info("sendResponse: to=$to, phoneNumberId=$phoneNumberId")

        // Request JSON exacto que exige Meta (formato WhatsApp Cloud API v19.0)
        val request = WhatsAppMessageRequest(to = to, text = TextBody(body = text))

        // Log del JSON exacto que se enviará a Meta
        val requestJson = Json.encodeToString(WhatsAppMessageRequest.serializer(), request)
        log.info("RAW REQUEST JSON:\n$requestJson")

        // Guarda el JSON completo de la request en Firebase antes de enviar
        val requestMap = request.toFirebaseMap()
        firebaseService.saveOutgoingRequest(to, requestMap)

        log.info("Enviando a WhatsApp API: messaging_product=${request.messaging_product}, recipient_type=${request.recipient_type}, to=$to, type=${request.type}")

        // POST https://graph.facebook.com/v19.0/{phoneNumberId}/messages con Bearer token
        val status = try {
            val response = whatsAppApiService.sendMessage(
                phoneNumberId = phoneNumberId,
                authorization = "Bearer ${com.example.AppConfig.accessToken}",
                request = request
            )
            if (response.isSuccessful) {
                log.info("WhatsApp API responded OK: status=${response.code()}, to=$to")
                response.code()
            } else {
                val errorBody = response.errorBody()?.string()
                log.error("WhatsApp API responded ERROR: status=${response.code()}, to=$to, phoneNumberId=$phoneNumberId, body=$errorBody")
                response.code()
            }
        } catch (e: Exception) {
            log.error("WhatsApp API call EXCEPTION: to=$to, phoneNumberId=$phoneNumberId, error=${e.message}", e)
            null
        }

        // Guarda resumen de la respuesta (OutgoingMessage) en conversations/{to}/messages/<pushId>/
        val outgoing = OutgoingMessage(
            from = phoneNumberId,
            to = to,
            body = text,
            status = if (status != null && status < 300) "sent" else "failed",
            statusCode = status,
            timestamp = System.currentTimeMillis()
        )

        val saved = firebaseService.saveOutgoing(outgoing)
        if (!saved) retryQueue.enqueue(PendingOutgoing(outgoing))

        log.info("sendResponse completado: to=$to, statusCode=$status")
        return status
    }
}
