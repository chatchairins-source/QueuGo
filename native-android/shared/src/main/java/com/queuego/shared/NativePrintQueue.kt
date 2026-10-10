package com.queuego.shared

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes all print jobs; persisted completion is checked inside the lock. */
class NativePrintQueue {
    private val mutex = Mutex()

    suspend fun submit(
        key: String?,
        force: Boolean = false,
        wasPrinted: (String) -> Boolean,
        markPrinted: suspend (String) -> Unit,
        send: suspend () -> Unit
    ): Boolean = mutex.withLock {
        if (key != null && !force && wasPrinted(key)) return@withLock false
        send()
        if (key != null) markPrinted(key)
        true
    }
}
