package com.example

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
    
    // Cache local para acceso instantáneo
    private val phoneRegistryCache = ConcurrentHashMap<String, PhoneData>()

    init {
        val optionsBuilder = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.getApplicationDefault())

        AppConfig.firebaseDatabaseUrl?.let { url ->
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

    // Fallback de lectura directa a Firebase RTDB cuando el cache local no tiene el dato
    // (útil en cold starts de Cloud Run donde el ValueEventListener aún no ha cargado los datos)
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

    private suspend fun saveMessage(conversationKey: String, data: Map<String, Any?>): Boolean = try {
        withContext(Dispatchers.IO) {
            db.child("conversations")
                .child(conversationKey)
                .child("messages")
                .push()
                .setValueAsync(data).get()
        }
        true
    } catch (e: Exception) {
        log.error("Failed to save message for $conversationKey: ${e.message}")
        false
    }

    suspend fun saveIncoming(msg: IncomingMessage): Boolean {
        val saved = saveMessage(msg.from, msg.toMap())
        if (saved) log.info("Incoming message saved in conversation with ${msg.from}")
        return saved
    }

    suspend fun saveOutgoing(msg: OutgoingMessage): Boolean {
        val saved = saveMessage(msg.to, msg.toMap())
        if (saved) log.info("Outgoing message saved in conversation with ${msg.to}")
        return saved
    }

    private fun DataSnapshot.toPhoneData(phoneNumberId: String? = null): PhoneData? {
        val id = phoneNumberId ?: this.key ?: return null
        val name = this.child("name").getValue(String::class.java) ?: ""
        val token = this.child("token").getValue(String::class.java) ?: ""
        return PhoneData(token = token, name = name, phoneNumberId = id)
    }
}
