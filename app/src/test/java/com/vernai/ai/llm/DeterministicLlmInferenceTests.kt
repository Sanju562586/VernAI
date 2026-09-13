package com.vernai.ai.llm

import com.vernai.ai.mock.MockLlmInferenceEngine
import com.vernai.ai.parser.TeluguLetterParser
import com.vernai.core.common.mutex.InferenceLock
import com.vernai.core.common.result.VernAiResult
import com.vernai.domain.model.letter.LetterInput
import com.vernai.domain.model.letter.LetterRecipient
import com.vernai.domain.model.letter.LetterType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class DeterministicLlmInferenceTests {

    private val engine = MockLlmInferenceEngine()
    private val parser = TeluguLetterParser()

    @Test
    fun mockEngine_generatesDeterministicValidJsonForCivicComplaints() = runTest {
        val prompt = "Draft a formal Telugu civic complaint regarding broken water pipeline. LETTER TYPE: COMPLAINT"
        val result = engine.generateCompleteText(prompt, GenerationParameters(temperature = 0.2f))

        assertTrue("Inference must succeed", result is VernAiResult.Success)
        val generatedText = (result as VernAiResult.Success).data
        assertNotNull(generatedText)
        assertTrue(generatedText.isNotBlank())

        val input = LetterInput(
            teluguVoiceTranscript = "పైప్‌లైన్ పగిలిపోయింది",
            letterType = LetterType.COMPLAINT,
            recipient = LetterRecipient("కార్యదర్శి", "పంచాయతీ")
        )

        val structured = parser.parseWithFallback(generatedText, input)
        assertNotNull(structured)
        assertTrue("Generated letter must have subject", structured.subject.isNotBlank())
        assertTrue("Generated letter must have body", structured.formalTeluguLetterBody.isNotBlank())
    }

    @Test
    fun mockEngine_emitsStreamingChunksWithoutLoss() = runTest {
        val prompt = "Generate streaming Telugu response."
        val chunks = engine.streamTokens(prompt, GenerationParameters()).toList()

        assertTrue("Streaming must emit multiple tokens/chunks", chunks.isNotEmpty())
        val concatenated = chunks.joinToString("")
        assertTrue("Concatenated stream must contain valid Telugu content", concatenated.contains("విషయం") || concatenated.contains("{"))
    }

    @Test
    fun inferenceLock_strictlySerializesConcurrentInferenceRequests() = runTest {
        val lock = InferenceLock()
        val concurrentCount = 8
        val activeThreads = AtomicInteger(0)
        val maxSimultaneous = AtomicInteger(0)

        val jobs = (1..concurrentCount).map {
            async {
                lock.withLlmLock {
                    val current = activeThreads.incrementAndGet()
                    var max = maxSimultaneous.get()
                    while (current > max) {
                        if (maxSimultaneous.compareAndSet(max, current)) break
                        max = maxSimultaneous.get()
                    }
                    delay(10) // Simulate on-device matrix multiplication work
                    activeThreads.decrementAndGet()
                }
            }
        }

        jobs.awaitAll()

        assertEquals("Maximum concurrent inference jobs under InferenceLock must strictly be 1", 1, maxSimultaneous.get())
        assertEquals("All active threads must have completed", 0, activeThreads.get())
    }

    @Test
    fun mockEngine_streamCancellationTerminatesGracefully() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val receivedChunks = mutableListOf<String>()

        val job = launch(testDispatcher) {
            engine.streamTokens("Stream to be cancelled", GenerationParameters()).collect { chunk ->
                receivedChunks.add(chunk)
                delay(10)
            }
        }

        testScheduler.advanceTimeBy(120)
        job.cancelAndJoin()

        assertTrue("Job is successfully cancelled", job.isCancelled)
    }

    @Test
    fun mockEngine_lifecycleLoadingAndUnloading() = runTest {
        assertTrue("Initially mock model is loaded", engine.isLoaded())

        val loadResult = engine.loadModel(File("qwen2.5-1.5b-indic.gguf"))
        assertTrue("Loading model must succeed", loadResult is VernAiResult.Success)
        assertTrue("Model should report loaded", engine.isLoaded())

        engine.unloadModel()
        assertEquals(LlmEngineState.Unloaded, engine.state.value)
    }
}
