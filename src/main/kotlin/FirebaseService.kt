package com.example

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

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
        log.info("Firebase Admin SDK initialized successfully")
    }

    private fun setupPhoneRegistryListener() {
        db.child("phoneRegistry").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newRegistry = mutableMapOf<String, PhoneData>()
                snapshot.children.forEach { child ->
                    val phoneNumberId = child.key ?: return@forEach
                    val name = child.child("name").getValue(String::class.java) ?: ""
                    val token = child.child("token").getValue(String::class.java) ?: ""
                    newRegistry[phoneNumberId] = PhoneData(token = token, name = name, phoneNumberId = phoneNumberId)
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

    suspend fun saveIncoming(msg: IncomingMessage): Boolean = try {
        withContext(Dispatchers.IO) {
            // Esquema: conversations/{customerPhone}/messages/{msgId}
            db.child("conversations")
              .child(msg.from)
              .child("messages")
              .push()
              .setValueAsync(msg.toMap()).get()
        }
        log.info("Incoming message saved in conversation with ${msg.from}")
        true
    } catch (e: Exception) {
        log.error("Failed to save incoming message: ${e.message}")
        false
    }

    suspend fun saveOutgoing(msg: OutgoingMessage): Boolean = try {
        withContext(Dispatchers.IO) {
            // Esquema: conversations/{customerPhone}/messages/{msgId}
            db.child("conversations")
              .child(msg.to)
              .child("messages")
              .push()
              .setValueAsync(msg.toMap()).get()
        }
        log.info("Outgoing message saved in conversation with ${msg.to}")
        true
    } catch (e: Exception) {
        log.error("Failed to save outgoing message: ${e.message}")
        false
    }
}
