package com.vernai

import android.app.Application
import com.vernai.core.common.dispatchers.DefaultVernAiDispatchers
import com.vernai.core.common.dispatchers.VernAiDispatchers
import com.vernai.core.common.mutex.InferenceLock
import com.vernai.core.database.VernAiDatabase

/**
 * Global Application class providing singleton dependencies for VernAI.
 */
class VernAiApplication : Application() {

    lateinit var database: VernAiDatabase
        private set

    val dispatchers: VernAiDispatchers by lazy {
        DefaultVernAiDispatchers()
    }

    val inferenceLock: InferenceLock by lazy {
        InferenceLock()
    }

    val asrEngine: com.vernai.ai.asr.AsrEngine by lazy {
        com.vernai.ai.asr.OnDeviceAsrEngine(this, dispatchers = dispatchers)
    }

    val memoryMonitor: com.vernai.core.common.memory.MemoryPressureMonitor by lazy {
        com.vernai.core.common.memory.AndroidMemoryPressureMonitor(this)
    }

    val llmEngine: com.vernai.ai.llm.LlmInferenceEngine by lazy {
        com.vernai.ai.llm.LlamaCppInferenceEngine(
            memoryMonitor = memoryMonitor,
            inferenceLock = inferenceLock,
            dispatchers = dispatchers
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = VernAiDatabase.create(this)
    }

    companion object {
        lateinit var instance: VernAiApplication
            private set
    }
}
