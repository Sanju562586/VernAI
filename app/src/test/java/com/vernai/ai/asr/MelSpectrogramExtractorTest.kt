package com.vernai.ai.asr

import com.vernai.ai.asr.audio.AudioRecordConfig
import com.vernai.ai.asr.model.MelSpectrogramExtractor
import com.vernai.ai.asr.model.TeluguAsrVocabulary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class MelSpectrogramExtractorTest {

    @Test
    fun testSpectrogramOutputDimensions() {
        val extractor = MelSpectrogramExtractor(
            sampleRate = 16000,
            nFft = 400,
            hopLength = 160,
            nMels = 80
        )

        // 1 second of 16kHz audio = 16,000 samples
        val audio = FloatArray(16000) { i ->
            (sin(2.0 * Math.PI * 440.0 * i / 16000.0)).toFloat() // 440 Hz pure tone
        }

        val spectrogram = extractor.extract(audio)

        // Expected nMels = 80
        assertEquals(80, spectrogram.size)

        // Expected numFrames: 1 + (16000 - 400) / 160 = 98 frames
        val expectedFrames = 1 + (16000 - 400) / 160
        assertEquals(expectedFrames, spectrogram[0].size)

        // Verify values are valid finite floating point numbers
        for (m in 0 until 80) {
            for (f in 0 until expectedFrames) {
                assertTrue("Spectrogram value should be finite", spectrogram[m][f].isFinite())
            }
        }
    }

    @Test
    fun testShortAudioPadding() {
        val extractor = MelSpectrogramExtractor()
        // Very short audio: 100 samples (less than nFft=400)
        val shortAudio = FloatArray(100) { 0.1f }
        val spectrogram = extractor.extract(shortAudio)

        assertEquals(80, spectrogram.size)
        assertTrue(spectrogram[0].isNotEmpty())
    }

    @Test
    fun testTeluguVocabularyDecoding() {
        val vocab = listOf("<blank>", "ఈరోజు", "5", "కేజీల", "టమాటా", "200", "రూపాయలు")
        // Sequence with CTC repeats and blank (0)
        val tokens = intArrayOf(0, 1, 1, 0, 2, 3, 3, 0, 4, 0, 5, 6, 6)

        val decoded = TeluguAsrVocabulary.decodeCtcTokens(tokens, vocab)
        assertEquals("ఈరోజు 5 కేజీల టమాటా 200 రూపాయలు", decoded)
    }

    @Test
    fun testTeluguTextNormalization() {
        val raw = "  ఈరోజు , 5 కేజీల టమాటా .  "
        val normalized = TeluguAsrVocabulary.normalizeTeluguText(raw)
        assertEquals("ఈరోజు, 5 కేజీల టమాటా.", normalized)
    }

    @Test
    fun testAudioRecordConfigCalculation() {
        val config = AudioRecordConfig(sampleRate = 16000, chunkDurationMs = 100)
        assertEquals(1600, config.samplesPerChunk)
        assertEquals(3200, config.bytesPerChunk)
    }
}
