package com.vernai.core.common.mutex

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Global execution coordinator for on-device native AI engines.
 * Prevents concurrent LLM prompt processing from colliding with heavy audio tensor allocation,
 * preserving resident memory limits on 8GB devices.
 */
class InferenceLock {
    private val llmMutex = Mutex()
    private val asrMutex = Mutex()
    private val globalComputeMutex = Mutex()

    suspend fun <T> withLlmLock(action: suspend () -> T): T {
        return llmMutex.withLock {
            action()
        }
    }

    suspend fun <T> withAsrLock(action: suspend () -> T): T {
        return asrMutex.withLock {
            action()
        }
    }

    suspend fun <T> withExclusiveHardwareLock(action: suspend () -> T): T {
        return globalComputeMutex.withLock {
            action()
        }
    }
}
