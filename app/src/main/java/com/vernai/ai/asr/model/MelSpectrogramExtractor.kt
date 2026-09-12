package com.vernai.ai.asr.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * On-device Log-Mel Spectrogram extractor for Indic speech models (Whisper / Conformer).
 * Standard parameters: 16 kHz sample rate, 400-sample (25ms) Hanning window, 160-sample (10ms) hop size,
 * 80 Mel filterbanks spanning 0 Hz to 8000 Hz.
 */
class MelSpectrogramExtractor(
    val sampleRate: Int = 16000,
    val nFft: Int = 400,
    val hopLength: Int = 160,
    val nMels: Int = 80
) {
    private val window: FloatArray = FloatArray(nFft) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / nFft))).toFloat()
    }

    private val melFilters: Array<FloatArray> = createMelFilterbank(
        sampleRate = sampleRate,
        nFft = nFft,
        nMels = nMels,
        fMin = 0f,
        fMax = 8000f
    )

    /**
     * Extracts an [nMels x numFrames] Log-Mel spectrogram from normalized 16kHz float audio.
     */
    fun extract(audio: FloatArray): Array<FloatArray> {
        if (audio.size < nFft) {
            // Pad audio if shorter than one FFT window
            val padded = FloatArray(nFft)
            System.arraycopy(audio, 0, padded, 0, audio.size)
            return extract(padded)
        }

        val numFrames = 1 + (audio.size - nFft) / hopLength
        val numFreqBins = nFft / 2 + 1
        val spectrogram = Array(nMels) { FloatArray(numFrames) }

        val frame = FloatArray(nFft)
        val real = FloatArray(nFft)
        val imag = FloatArray(nFft)
        val powerSpectrum = FloatArray(numFreqBins)

        for (frameIdx in 0 until numFrames) {
            val startSample = frameIdx * hopLength

            // Apply Hanning window
            for (i in 0 until nFft) {
                frame[i] = audio[startSample + i] * window[i]
                real[i] = frame[i]
                imag[i] = 0f
            }

            // Compute DFT for frequencies up to Nyquist
            for (k in 0 until numFreqBins) {
                var sumReal = 0.0
                var sumImag = 0.0
                val angleFactor = 2.0 * PI * k / nFft
                for (n in 0 until nFft) {
                    val angle = angleFactor * n
                    sumReal += frame[n] * cos(angle)
                    sumImag -= frame[n] * sin(angle)
                }
                powerSpectrum[k] = ((sumReal * sumReal + sumImag * sumImag) / nFft).toFloat()
            }

            // Apply Mel filterbanks
            for (m in 0 until nMels) {
                var melEnergy = 0.0f
                val filter = melFilters[m]
                for (k in 0 until numFreqBins) {
                    melEnergy += powerSpectrum[k] * filter[k]
                }
                // Log compression (log10 with clamp)
                spectrogram[m][frameIdx] = log10(max(melEnergy, 1e-5f))
            }
        }

        return spectrogram
    }

    companion object {
        private fun hzToMel(hz: Float): Float = 2595f * log10(1f + hz / 700f)
        private fun melToHz(mel: Float): Float = 700f * (Math.pow(10.0, (mel / 2595.0)) - 1.0).toFloat()

        private fun createMelFilterbank(
            sampleRate: Int,
            nFft: Int,
            nMels: Int,
            fMin: Float,
            fMax: Float
        ): Array<FloatArray> {
            val numFreqBins = nFft / 2 + 1
            val minMel = hzToMel(fMin)
            val maxMel = hzToMel(fMax)

            val melPoints = FloatArray(nMels + 2) { i ->
                minMel + i * (maxMel - minMel) / (nMels + 1)
            }
            val binPoints = IntArray(nMels + 2) { i ->
                val hz = melToHz(melPoints[i])
                ((nFft + 1) * hz / sampleRate).toInt().coerceIn(0, numFreqBins - 1)
            }

            val filterBank = Array(nMels) { FloatArray(numFreqBins) }
            for (m in 0 until nMels) {
                val left = binPoints[m]
                val center = binPoints[m + 1]
                val right = binPoints[m + 2]

                for (k in left until center) {
                    if (center != left) {
                        filterBank[m][k] = (k - left).toFloat() / (center - left)
                    }
                }
                for (k in center until right) {
                    if (right != center) {
                        filterBank[m][k] = (right - k).toFloat() / (right - center)
                    }
                }
            }
            return filterBank
        }
    }
}
