package com.example

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    suspend fun processWebhook(json: JsonObject, phoneNumberId: String, phoneData: PhoneData?) {
        val value = json.extractWebhookValue()

        val messages = value?.get("messages")?.jsonArray
        if (messages != null && messages.isNotEmpty()) {
            val msg = messages[0].jsonObject
            val from = msg["from"]?.jsonPrimitive?.content ?: return
            val msgBody = msg["text"]?.jsonObject?.get("body")?.jsonPrimitive?.content ?: ""
            val msgId = msg["id"]?.jsonPrimitive?.content ?: "unknown_${System.currentTimeMillis()}"
            val rawTs = msg["timestamp"]?.jsonPrimitive?.content
            val timestamp = (rawTs?.toLongOrNull() ?: System.currentTimeMillis()) * 1000L

            val incoming = IncomingMessage(
                from = from,
                to = phoneNumberId,
                body = msgBody,
                messageId = msgId,
                timestamp = timestamp
            )

            // Guardado inmediato en BDD (siempre)
            val saved = firebaseService.saveIncoming(incoming)
            if (!saved) retryQueue.enqueue(PendingIncoming(incoming))

            // Enviar respuesta automática solo si tenemos el registro del emisor
            if (phoneData != null) {
                sendResponse(from, "¡Hola! Recibimos tu mensaje en nuestro servidor.", phoneNumberId)
            }
        }
    }

    suspend fun sendResponse(to: String, text: String, phoneNumberId: String): Int? {
        log.info("Sending response to $to via $phoneNumberId...")
        
        val status = try {
            val request = WhatsAppMessageRequest(to = to, text = TextBody(body = text))
            log.info("WhatsApp request body: messaging_product=${request.messaging_product}, recipient_type=${request.recipient_type}, to=$to, type=${request.type}")
            val response = whatsAppApiService.sendMessage(
                phoneNumberId = phoneNumberId,
                authorization = "Bearer ${AppConfig.accessToken}",
                request = request
            )
            if (response.isSuccessful) {
                log.info("Message sent successfully to $to (${response.code()})")
                response.code()
            } else {
                val errorBody = response.errorBody()?.string()
                log.error("WhatsApp API Error ${response.code()} to=$to phoneNumberId=$phoneNumberId: $errorBody")
                response.code()
            }
        } catch (e: Exception) {
            log.error("Exception sending message to $to phoneNumberId=$phoneNumberId: ${e.message}", e)
            null
        }

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
        
        return status
    }
}

fun JsonObject.extractWebhookValue(): JsonObject? {
    return this["entry"]?.jsonArray?.getOrNull(0)?.jsonObject
        ?.get("changes")?.jsonArray?.getOrNull(0)?.jsonObject
        ?.get("value")?.jsonObject
}
