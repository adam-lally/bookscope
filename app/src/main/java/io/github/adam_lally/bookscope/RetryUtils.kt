package io.github.adam_lally.bookscope

import kotlinx.coroutines.delay

/**
 * Retry [block] up to [attempts] using an exponential backoff strategy.
 */
suspend fun <T> retryWithBackoff(
    attempts: Int = 3,
    initialDelayMillis: Long = 1000,
    factor: Double = 2.0,
    block: suspend () -> T
): T {
    var currentDelay = initialDelayMillis
    repeat(attempts - 1) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            if (attempt == attempts - 1) throw e
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong()
        }
    }
    return block()
}
