package com.example.service

import com.example.OutgoingMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentLinkedQueue

class RetryQueue(private val firebase: FirebaseService?) {
    private val log = LoggerFactory.getLogger(RetryQueue::class.java)
    private val pending = ConcurrentLinkedQueue<PendingMessage>()
    private val maxRetries = 10
    private val pollIntervalMs = 30_000L

    fun enqueue(msg: PendingMessage) {
        pending.add(msg)
        log.warn("Message queued for retry [${msg.id}], queue size: ${pending.size}")
    }

    fun start(scope: CoroutineScope) {
        scope.launch {
            log.info("RetryQueue worker started (poll every ${pollIntervalMs}ms)")
            while (isActive) {
                delay(pollIntervalMs)
                processBatch()
            }
        }
    }

    private suspend fun processBatch() {
        val iterator = pending.iterator()
        while (iterator.hasNext()) {
            val msg = iterator.next()
            try {
                val success = msg.execute(firebase)
                if (success) {
                    iterator.remove()
                    log.info("Message recovered from retry queue: ${msg.id}")
                } else {
                    msg.retryCount++
                    if (msg.retryCount > maxRetries) {
                        iterator.remove()
                        log.error("Message LOST after $maxRetries retries: ${msg.id}")
                    }
                }
            } catch (e: Exception) {
                msg.retryCount++
                if (msg.retryCount > maxRetries) {
                    iterator.remove()
                    log.error("Message LOST after $maxRetries retries: ${msg.id}, error: ${e.message}")
                }
            }
        }
    }
}

sealed class PendingMessage(open val id: String, open var retryCount: Int = 0) {
    abstract suspend fun execute(firebase: FirebaseService?): Boolean
}

class PendingOutgoing(
    private val msg: OutgoingMessage
) : PendingMessage(id = "${msg.to}_${msg.timestamp}") {
    override suspend fun execute(firebase: FirebaseService?): Boolean =
        firebase?.saveOutgoing(msg) ?: false
}

class PendingWebhookMessage(
    private val conversationKey: String,
    private val messageMap: Map<String, Any?>
) : PendingMessage(id = messageMap["id"] as? String ?: "unknown") {
    override suspend fun execute(firebase: FirebaseService?): Boolean =
        firebase?.saveWebhookMessage(conversationKey, messageMap) ?: false
}
