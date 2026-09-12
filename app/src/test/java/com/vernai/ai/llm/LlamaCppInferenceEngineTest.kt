package com.vernai.ai.llm

import com.vernai.ai.llm.llama.LlamaBridge
import com.vernai.ai.modelmanager.SupportedModels
import com.vernai.core.common.dispatchers.DefaultVernAiDispatchers
import com.vernai.core.common.memory.MemoryPressureLevel
import com.vernai.core.common.memory.MemoryPressureMonitor
import com.vernai.core.common.mutex.InferenceLock
import com.vernai.core.common.result.VernAiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LlamaCppInferenceEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeMemoryPressureMonitor(
        var customAvailableMemoryMb: Long = 4000L,
        var isSafe: Boolean = true
    ) : MemoryPressureMonitor {
        private val _pressureLevel = MutableStateFlow(MemoryPressureLevel.NORMAL)
        override val pressureLevel: StateFlow<MemoryPressureLevel> = _pressureLevel

        fun setPressure(level: MemoryPressureLevel) {
            _pressureLevel.value = level
        }

        override fun getAvailableMemoryMb(): Long = customAvailableMemoryMb
        override fun isSafeForLlmInference(requiredMemoryMb: Long): Boolean = isSafe
    }

    @Test
    fun testModelFileNotFoundReturnsError() = runTest {
        val nonExistentFile = File(tempFolder.root, "non_existent_model.gguf")
        val engine = LlamaCppInferenceEngine(
            llamaBridge = LlamaBridge(),
            inferenceLock = InferenceLock(),
            dispatchers = DefaultVernAiDispatchers()
        )

        val result = engine.loadModel(nonExistentFile)

        assertTrue(result is VernAiResult.Error)
        val errorMsg = (result as VernAiResult.Error).message ?: ""
        assertTrue(errorMsg.contains("not found or unreadable"))
        assertFalse(engine.isLoaded())
        assertTrue(engine.state.value is LlmEngineState.Error)
    }

    @Test
    fun testInsufficientMemoryHandling() = runTest {
        val dummyModelFile = tempFolder.newFile("gemma-3-1b-it-q4_k_m.gguf").apply {
            writeBytes(ByteArray(1024))
        }

        val fakeMemory = FakeMemoryPressureMonitor(
            customAvailableMemoryMb = 500L, // Only 500MB available
            isSafe = false // Guard triggers
        )

        val engine = LlamaCppInferenceEngine(
            llamaBridge = LlamaBridge(),
            memoryMonitor = fakeMemory,
            inferenceLock = InferenceLock(),
            dispatchers = DefaultVernAiDispatchers()
        )

        val result = engine.loadModel(dummyModelFile)

        assertTrue("Should reject load on insufficient memory", result is VernAiResult.Error)
        val errorMsg = (result as VernAiResult.Error).message ?: ""
        assertTrue(errorMsg.contains("Insufficient device RAM"))
        assertFalse(engine.isLoaded())
        assertTrue(engine.state.value is LlmEngineState.Error)
    }

    @Test
    fun testSuccessfulModelLoadAndUnloadLifecycle() = runTest {
        val dummyModelFile = tempFolder.newFile("gemma-3-1b-it-q4_k_m.gguf").apply {
            writeBytes(ByteArray(1024))
        }

        val fakeMemory = FakeMemoryPressureMonitor(customAvailableMemoryMb = 3500L, isSafe = true)
        val engine = LlamaCppInferenceEngine(
            llamaBridge = LlamaBridge(),
            memoryMonitor = fakeMemory,
            inferenceLock = InferenceLock(),
            dispatchers = DefaultVernAiDispatchers()
        )

        assertEquals(LlmEngineState.Unloaded, engine.state.value)

        // Load with custom context length
        val loadResult = engine.loadModel(dummyModelFile, contextLength = 2048, nThreads = 4)
        assertTrue(loadResult is VernAiResult.Success)
        assertTrue(engine.isLoaded())
        assertEquals(LlmEngineState.Ready, engine.state.value)

        // Unload
        engine.unloadModel()
        assertFalse(engine.isLoaded())
        assertEquals(LlmEngineState.Unloaded, engine.state.value)
    }

    @Test
    fun testTokenStreamingAndThroughput() = runTest {
        val dummyModelFile = tempFolder.newFile("qwen2.5-1.5b-instruct-q4_k_m.gguf").apply {
            writeBytes(ByteArray(1024))
        }

        val engine = LlamaCppInferenceEngine(
            llamaBridge = LlamaBridge(),
            inferenceLock = InferenceLock(),
            dispatchers = DefaultVernAiDispatchers()
        )

        engine.loadModel(dummyModelFile)
        assertTrue(engine.isLoaded())

        val tokens = mutableListOf<String>()
        engine.streamTokens("గ్రామ పంచాయతీ వీధి దీపాలు ఫిర్యాదు").collect { token ->
            tokens.add(token)
        }

        assertTrue("Should stream multiple tokens", tokens.size > 2)
        val fullText = tokens.joinToString("")
        assertTrue("Generated Telugu text should contain complaint vocabulary", fullText.contains("పంచాయతీ") || fullText.contains("దీపాలు"))
        assertEquals(LlmEngineState.Ready, engine.state.value)
    }

    @Test
    fun testCancellationHaltsStreamingImmediately() = runTest {
        val dummyModelFile = tempFolder.newFile("model.gguf").apply {
            writeBytes(ByteArray(1024))
        }

        val engine = LlamaCppInferenceEngine(
            llamaBridge = LlamaBridge(),
            inferenceLock = InferenceLock(),
            dispatchers = DefaultVernAiDispatchers()
        )

        engine.loadModel(dummyModelFile)

        // Launch stream in background and cancel immediately after first token
        val emittedTokens = mutableListOf<String>()
        val job = launch {
            engine.streamTokens("ఫిర్యాదు").collect { token ->
                emittedTokens.add(token)
            }
        }

        // Cancel job
        job.cancel()
        job.join()

        // Verify state is safely reset to Ready after cancellation
        assertEquals(LlmEngineState.Ready, engine.state.value)
    }

    @Test
    fun testGrammarConstrainedSalesLogGeneration() = runTest {
        val dummyModelFile = tempFolder.newFile("model.gguf").apply {
            writeBytes(ByteArray(1024))
        }

        val engine = LlamaCppInferenceEngine(
            llamaBridge = LlamaBridge(),
            inferenceLock = InferenceLock(),
            dispatchers = DefaultVernAiDispatchers()
        )

        engine.loadModel(dummyModelFile)

        val params = GenerationParameters(
            grammar = GrammarConstraint.SALES_LOG_JSON
        )
        val result = engine.generateCompleteText("ఈరోజు 5 కేజీల టమాటా 200 రూపాయలు అమ్మిన", params)

        assertTrue(result is VernAiResult.Success)
        val jsonOutput = (result as VernAiResult.Success).data
        assertTrue("Output should be valid JSON containing items array", jsonOutput.contains("\"items\""))
        assertTrue("Should contain tomato item", jsonOutput.contains("టమాటా"))
    }

    @Test
    fun testSupportedModelsMetadata() {
        val gemma = SupportedModels.GEMMA_3_1B_IT_Q4_K_M
        assertEquals("gemma-3-1b-it-q4_k_m", gemma.id)
        assertTrue("Gemma size should be ~850MB-950MB", gemma.expectedSizeBytes in 800_000_000L..1_000_000_000L)
        assertEquals(1400L, gemma.ramRequiredMb)

        val qwen = SupportedModels.QWEN_2_5_1_5B_INSTRUCT_Q4_K_M
        assertEquals("qwen-2.5-1.5b-instruct-q4_k_m", qwen.id)
        assertEquals(1650L, qwen.ramRequiredMb)
    }
}
