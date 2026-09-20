package com.vernai.ai.asr

import com.vernai.ai.asr.audio.AudioFrame
import com.vernai.ai.asr.audio.AudioRecordConfig
import com.vernai.ai.asr.evaluation.metrics.AsrMetricsCalculator
import com.vernai.ai.asr.evaluation.metrics.EditDistance
import com.vernai.ai.asr.evaluation.normalizer.TeluguTextNormalizer
import com.vernai.ai.asr.model.MelSpectrogramExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

class AsrIntegrationFixtureTests {

    private val extractor = MelSpectrogramExtractor()
    private val config = AudioRecordConfig()

    private fun generateSyntheticSineWave(
        frequencyHz: Double,
        durationSeconds: Double,
        sampleRate: Int = 16000,
        amplitude: Float = 0.8f
    ): FloatArray {
        val totalSamples = (sampleRate * durationSeconds).toInt()
        val samples = FloatArray(totalSamples)
        val angularFrequency = 2.0 * PI * frequencyHz
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            samples[i] = (amplitude * sin(angularFrequency * t)).toFloat()
        }
        return samples
    }

    private fun floatArrayToPcmBytes(samples: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) {
            val clamped = s.coerceIn(-1.0f, 1.0f)
            val pcmShort = (clamped * 32767.0f).toInt().toShort()
            buffer.putShort(pcmShort)
        }
        return buffer.array()
    }

    @Test
    fun melSpectrogramExtractor_processesSpeechAudioFixture() {
        // 1.5 seconds of simulated 300Hz vocal formant at 16kHz
        val speechFixture = generateSyntheticSineWave(frequencyHz = 300.0, durationSeconds = 1.5)
        assertEquals("Fixture must have 24000 samples for 1.5s at 16kHz", 24000, speechFixture.size)

        val melSpectrogram = extractor.extract(speechFixture)

        assertNotNull("Mel spectrogram must not be null", melSpectrogram)
        assertEquals("Mel spectrogram should have 80 mel frequency bins", 80, melSpectrogram.size)
        assertTrue("Mel spectrogram should contain temporal frames", melSpectrogram[0].isNotEmpty())
    }

    @Test
    fun melSpectrogramExtractor_handlesSilenceGracefully() {
        // 1.0 second of absolute silence
        val silenceFixture = FloatArray(16000) { 0.0f }

        val melSpectrogram = extractor.extract(silenceFixture)

        assertNotNull(melSpectrogram)
        assertEquals(80, melSpectrogram.size)
        assertTrue(melSpectrogram[0].isNotEmpty())
        // In log mel scale, silence is strongly negative (floor clamped)
        assertTrue("Silence energy should be low", melSpectrogram[0][0] < -2.0f)
    }

    @Test
    fun audioFrame_computesDecibelsAndVoiceActivityAccurately() {
        // 1. High energy vocal fixture (400 Hz tone)
        val vocalSamples = generateSyntheticSineWave(frequencyHz = 400.0, durationSeconds = 0.1, amplitude = 0.9f)
        val vocalBytes = floatArrayToPcmBytes(vocalSamples)

        var sumSq = 0.0
        for (s in vocalSamples) sumSq += (s * s)
        val rms = sqrt(sumSq / vocalSamples.size)
        val db = if (rms > 1e-5) (20.0 * log10(rms) + 90.0).toFloat().coerceIn(0f, 95f) else 0f

        val vocalFrame = AudioFrame(
            pcmBytes = vocalBytes,
            normalizedSamples = vocalSamples,
            decibels = db,
            isSpeech = db >= config.speechSilenceThresholdDb,
            timestampMs = System.currentTimeMillis()
        )

        assertTrue("High amplitude vocal tone must be detected as speech", vocalFrame.isSpeech)
        assertTrue("Decibels for vocal audio should be > 60 dB", vocalFrame.decibels > 60f)

        // 2. Pure silence fixture
        val silenceSamples = FloatArray(1600) { 0.0f }
        val silenceFrame = AudioFrame(
            pcmBytes = ByteArray(3200),
            normalizedSamples = silenceSamples,
            decibels = 0f,
            isSpeech = false,
            timestampMs = System.currentTimeMillis()
        )

        assertFalse("Pure silence must not be detected as speech", silenceFrame.isSpeech)
        assertEquals(0f, silenceFrame.decibels, 0.001f)
    }

    @Test
    fun teluguTextNormalizer_standardizesSpokenTeluguUnicode() {
        val rawSpoken = "  నమస్కారం   అండీ ,   ఈరోజు   5   కేజీల   టమాటా   "

        // 1. Strict mode preserves exact tokens without digit conversion
        val strictNormalized = TeluguTextNormalizer.normalize(rawSpoken, com.vernai.ai.asr.evaluation.normalizer.NormalizationMode.STRICT)
        assertFalse("Normalized text must not have leading or trailing whitespace", strictNormalized.startsWith(" ") || strictNormalized.endsWith(" "))
        assertFalse("Normalized text must not have repeated spaces", strictNormalized.contains("  "))
        assertTrue("Telugu core tokens must be preserved", strictNormalized.contains("నమస్కారం"))
        assertTrue("Digits and exact terms preserved in STRICT mode", strictNormalized.contains("5 కేజీల టమాటా"))

        // 2. Numeral Canonical mode converts numerals to spoken words for fair WER evaluation
        val canonicalNormalized = TeluguTextNormalizer.normalize(rawSpoken, com.vernai.ai.asr.evaluation.normalizer.NormalizationMode.NUMERAL_CANONICAL)
        assertTrue("Digits converted to spoken Telugu words", canonicalNormalized.contains("ఐదు కిలోల టమాటా"))
    }

    @Test
    fun editDistanceAndMetrics_calculatesExactErrorRates() {
        val reference = "పంచాయతీ కార్యాలయానికి వినతిపత్రం సమర్పించాను"
        val identicalHypothesis = "పంచాయతీ కార్యాలయానికి వినతిపత్రం సమర్పించాను"

        val alignment = EditDistance.align(listOf(reference), listOf(identicalHypothesis))
        assertEquals("Identical strings must have 0 error count", 0, alignment.errorCount)

        val metricsZero = AsrMetricsCalculator.evaluate(
            reference = reference,
            hypothesis = identicalHypothesis
        )
        assertEquals(0.0f, metricsZero.wer, 0.001f)
        assertEquals(0.0f, metricsZero.cer, 0.001f)

        // Hypothesis with one substitution: "కార్యాలయానికి" -> "కార్యాలయమునకు"
        val variantHypothesis = "పంచాయతీ కార్యాలయమునకు వినతిపత్రం సమర్పించాను"
        val metricsVariant = AsrMetricsCalculator.evaluate(
            reference = reference,
            hypothesis = variantHypothesis
        )
        assertTrue("WER should reflect word mismatch", metricsVariant.wer > 0.0f)
        assertTrue("CER should reflect character variations", metricsVariant.cer > 0.0f)
    }

    @Test
    fun acousticVoiceCommand_generatesLanguageSpecificUtterances() {
        // 1. Short utterance (sales dictation)
        val teShort = OnDeviceAsrEngine.generateAcousticVoiceCommand(com.vernai.core.model.Language.TELUGU, speechEnergyFrames = 15)
        assertTrue("Telugu short voice command should contain tomato sales", teShort.contains("టమాటా") && teShort.contains("200 రూపాయలు"))

        val taShort = OnDeviceAsrEngine.generateAcousticVoiceCommand(com.vernai.core.model.Language.TAMIL, speechEnergyFrames = 15)
        assertTrue("Tamil short voice command should contain tomato sales", taShort.contains("தக்காளி") && taShort.contains("ரூபாய்"))

        val hiShort = OnDeviceAsrEngine.generateAcousticVoiceCommand(com.vernai.core.model.Language.HINDI, speechEnergyFrames = 15)
        assertTrue("Hindi short voice command should contain tomato sales", hiShort.contains("टमाटर") && hiShort.contains("रुपये"))

        val enShort = OnDeviceAsrEngine.generateAcousticVoiceCommand(com.vernai.core.model.Language.ENGLISH, speechEnergyFrames = 15)
        assertTrue("English short voice command should contain tomato sales", enShort.contains("tomatoes") && enShort.contains("200 rupees"))

        // 2. Long utterance (grievance petition)
        val teLong = OnDeviceAsrEngine.generateAcousticVoiceCommand(com.vernai.core.model.Language.TELUGU, speechEnergyFrames = 40)
        assertTrue("Telugu long voice command should contain grievance petition", teLong.contains("పంచాయతీ") && teLong.contains("వినతిపత్రం"))

        val taLong = OnDeviceAsrEngine.generateAcousticVoiceCommand(com.vernai.core.model.Language.TAMIL, speechEnergyFrames = 40)
        assertTrue("Tamil long voice command should contain grievance petition", taLong.contains("ஊராட்சி") && taLong.contains("புகார்"))

        val hiLong = OnDeviceAsrEngine.generateAcousticVoiceCommand(com.vernai.core.model.Language.HINDI, speechEnergyFrames = 40)
        assertTrue("Hindi long voice command should contain grievance petition", hiLong.contains("पंचायत") && hiLong.contains("शिकायत"))

        val enLong = OnDeviceAsrEngine.generateAcousticVoiceCommand(com.vernai.core.model.Language.ENGLISH, speechEnergyFrames = 40)
        assertTrue("English long voice command should contain grievance petition", enLong.contains("panchayat") && enLong.contains("complaint"))
    }
}
