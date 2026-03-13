package com.example.spacer.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit


object SupabaseRequestGuard {
    private const val MAX_CONCURRENT_REQUESTS = 6
    private const val MAX_ATTEMPTS = 3
    private const val BASE_BACKOFF_MS = 400L
    private val semaphore = Semaphore(MAX_CONCURRENT_REQUESTS)

    suspend fun <T> run(block: suspend () -> T): T {
        return semaphore.withPermit {
            var attempt = 0
            var lastError: Throwable? = null
            while (attempt < MAX_ATTEMPTS) {
                attempt++
                try {
                    return@withPermit block()
                } catch (t: Throwable) {
                    lastError = t
                    if (!isRetryable(t) || attempt >= MAX_ATTEMPTS) break
                    val backoff = BASE_BACKOFF_MS * (1L shl (attempt - 1))
                    delay(backoff)
                }
            }
            throw (lastError ?: IllegalStateException("Supabase request failed"))
        }
    }

    private fun isRetryable(t: Throwable): Boolean {
        val message = t.message?.lowercase().orEmpty()
        return "connection timeout" in message ||
            "connection terminated" in message ||
            "timeout" in message ||
            "temporarily unavailable" in message ||
            "too many connections" in message
    }
}
