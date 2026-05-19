package com.example

import com.google.api.core.ApiFuture
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

class FirebaseService {
    private val log = LoggerFactory.getLogger(FirebaseService::class.java)
    private val db: DatabaseReference

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
        log.info("Firebase Admin SDK initialized successfully")
    }

    suspend fun saveIncoming(msg: IncomingMessage): Boolean = try {
        withContext(Dispatchers.IO) {
            db.child("messages/incoming").push().setValueAsync(msg.toMap()).get()
        }
        log.info("Incoming message saved: ${msg.messageId}")
        true
    } catch (e: Exception) {
        log.error("Failed to save incoming message: ${e.message}")
        false
    }

    suspend fun saveOutgoing(msg: OutgoingMessage): Boolean = try {
        withContext(Dispatchers.IO) {
            db.child("messages/outgoing").push().setValueAsync(msg.toMap()).get()
        }
        log.info("Outgoing message saved: to=${msg.to}")
        true
    } catch (e: Exception) {
        log.error("Failed to save outgoing message: ${e.message}")
        false
    }
}
