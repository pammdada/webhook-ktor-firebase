package com.example.service

import com.example.OutgoingMessage
import com.example.whatsapp.PhoneData
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class FirebaseService {
    private val log = LoggerFactory.getLogger(FirebaseService::class.java)
    private val db: DatabaseReference
    
    private val phoneRegistryCache = ConcurrentHashMap<String, PhoneData>()

    init {
        val optionsBuilder = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.getApplicationDefault())

        com.example.AppConfig.firebaseDatabaseUrl?.let { url ->
            optionsBuilder.setDatabaseUrl(url)
            log.info("Using explicit database URL: $url")
        }

        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(optionsBuilder.build())
        }
        db = FirebaseDatabase.getInstance().reference
        
        setupPhoneRegistryListener()
        preloadPhoneRegistryBlocking()
        log.info("Firebase Admin SDK initialized successfully")
    }

    private fun setupPhoneRegistryListener() {
        db.child("phoneRegistry").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newRegistry = mutableMapOf<String, PhoneData>()
                snapshot.children.forEach { child ->
                    child.toPhoneData()?.let { newRegistry[it.phoneNumberId] = it }
                }
                phoneRegistryCache.clear()
                phoneRegistryCache.putAll(newRegistry)
                log.info("Phone registry cache updated: ${phoneRegistryCache.size} entries")
            }

            override fun onCancelled(error: DatabaseError) {
                log.error("Registry listener cancelled: ${error.message}")
            }
        })
    }

    fun getCachedPhoneRegistry(): Map<String, PhoneData> = phoneRegistryCache

    private fun preloadPhoneRegistryBlocking() {
        try {
            val latch = CountDownLatch(1)
            db.child("phoneRegistry").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    snapshot.children.forEach { child ->
                        child.toPhoneData()?.let { phoneRegistryCache[it.phoneNumberId] = it }
                    }
                    log.info("Phone registry preloaded (blocking): ${phoneRegistryCache.size} entries")
                    latch.countDown()
                }

                override fun onCancelled(error: DatabaseError) {
                    log.error("Phone registry preload cancelled: ${error.message}")
                    latch.countDown()
                }
            })
            if (!latch.await(10, TimeUnit.SECONDS)) {
                log.warn("Phone registry preload timed out after 10s")
            }
        } catch (e: Exception) {
            log.error("Failed to preload phone registry: ${e.message}")
        }
    }

    suspend fun getPhoneDataDirectly(phoneNumberId: String): PhoneData? = withContext(Dispatchers.IO) {
        try {
            val completableFuture = CompletableFuture<PhoneData?>()
            db.child("phoneRegistry").child(phoneNumberId)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        completableFuture.complete(snapshot.toPhoneData(phoneNumberId))
                    }

                    override fun onCancelled(error: DatabaseError) {
                        log.error("Direct phoneRegistry read cancelled for $phoneNumberId: ${error.message}")
                        completableFuture.completeExceptionally(Exception(error.message))
                    }
                })
            completableFuture.get(5, TimeUnit.SECONDS)
        } catch (e: Exception) {
            log.error("Direct phoneRegistry read failed for $phoneNumberId: ${e.message}")
            null
        }
    }

    // Guarda cada mensaje individual bajo conversations/{key}/messages/<pushId>/
    // data = mapa del mensaje (WebhookMessage.toFirebaseMap())
    private suspend fun saveMessage(conversationKey: String, data: Map<String, Any?>): String? = try {
        withContext(Dispatchers.IO) {
            val ref = db.child("conversations")
                .child(conversationKey)
                .child("messages")
                .push()
            ref.setValueAsync(data).get()
            ref.key
        }
    } catch (e: Exception) {
        log.error("Failed to save message for $conversationKey: ${e.message}")
        null
    }

    // Guarda el webhook completo tal cual lo recibió Meta en rawWebhookPayloads/<pushId>/
    // payloadMap = WebhookPayload.toFirebaseMap() (contiene entry[], changes[], messages[], metadata, etc.)
    suspend fun saveRawWebhookPayload(payloadMap: Map<String, Any?>): Boolean = try {
        withContext(Dispatchers.IO) {
            val ref = db.child("rawWebhookPayloads").push()
            ref.setValueAsync(payloadMap).get()
            val key = ref.key
            log.info("Raw webhook payload saved → rawWebhookPayloads/$key")
        }
        true
    } catch (e: Exception) {
        log.error("Failed to save raw webhook payload: ${e.message}")
        false
    }

    // Guarda la request exacta que se envía a WhatsApp Cloud API en conversations/{key}/outgoingRequests/<pushId>/
    // requestMap = WhatsAppMessageRequest.toFirebaseMap() (messaging_product, recipient_type, to, type, text.body)
    suspend fun saveOutgoingRequest(conversationKey: String, requestMap: Map<String, Any?>): Boolean = try {
        withContext(Dispatchers.IO) {
            val ref = db.child("conversations")
                .child(conversationKey)
                .child("outgoingRequests")
                .push()
            ref.setValueAsync(requestMap).get()
            val key = ref.key
            log.info("Outgoing request saved → conversations/$conversationKey/outgoingRequests/$key")
        }
        true
    } catch (e: Exception) {
        log.error("Failed to save outgoing request for $conversationKey: ${e.message}")
        false
    }

    // Guarda resumen de la respuesta enviada (OutgoingMessage) bajo conversations/{key}/messages/<pushId>/
    suspend fun saveOutgoing(msg: OutgoingMessage): Boolean {
        val key = saveMessage(msg.to, msg.toMap())
        if (key != null) log.info("Outgoing message saved → conversations/${msg.to}/messages/$key")
        else log.error("Failed to save outgoing message for ${msg.to}")
        return key != null
    }

    // Guarda cada mensaje del webhook (WebhookMessage) bajo conversations/{key}/messages/<pushId>/
    // messageMap = WebhookMessage.toFirebaseMap() (from, id, timestamp, type, text.body, image, etc.)
    suspend fun saveWebhookMessage(conversationKey: String, messageMap: Map<String, Any?>): Boolean {
        val key = saveMessage(conversationKey, messageMap)
        if (key != null) log.info("Webhook message saved → conversations/$conversationKey/messages/$key")
        else log.error("Failed to save webhook message for $conversationKey")
        return key != null
    }

    private fun DataSnapshot.toPhoneData(phoneNumberId: String? = null): PhoneData? {
        val id = phoneNumberId ?: this.key ?: return null
        val name = this.child("name").getValue(String::class.java) ?: ""
        val token = this.child("token").getValue(String::class.java) ?: ""
        return PhoneData(token = token, name = name, phoneNumberId = id)
    }
}
