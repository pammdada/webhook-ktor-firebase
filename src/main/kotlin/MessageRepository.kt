package com.example

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

    // Guarda el mensaje completo del webhook en Firebase (formato nativo de Meta)
    suspend fun processWebhookMessage(message: WebhookMessage, phoneNumberId: String, phoneData: PhoneData?) {
        // Convierte WebhookMessage → Map<String, Any?> usando kotlinx.serialization JSON
        val messageMap = message.toFirebaseMap()
        // Guarda en: conversations/{from}/messages/{pushId}/
        val saved = firebaseService.saveWebhookMessage(message.from, messageMap)
        // Si falla Firebase, encola para reintento en background cada 30s
        if (!saved) retryQueue.enqueue(PendingWebhookMessage(message.from, messageMap))

        // Respuesta automática solo si tenemos el token del phoneNumberId
        if (phoneData != null) {
            sendResponse(message.from, "¡Hola! Recibimos tu mensaje en nuestro servidor.", phoneNumberId)
        }
    }

    // Construye WhatsAppMessageRequest y llama a WhatsApp Cloud API v19.0
    suspend fun sendResponse(to: String, text: String, phoneNumberId: String): Int? {
        log.info("Sending response to $to via $phoneNumberId...")
        
        val status = try {
            // Formato JSON que exige Meta: messaging_product, recipient_type, to, type, text
            val request = WhatsAppMessageRequest(to = to, text = TextBody(body = text))
            log.info("WhatsApp request body: messaging_product=${request.messaging_product}, recipient_type=${request.recipient_type}, to=$to, type=${request.type}")
            // POST https://graph.facebook.com/v19.0/{phoneNumberId}/messages con Bearer token
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

        // Guarda copia de la respuesta enviada en Firebase para auditoría
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
